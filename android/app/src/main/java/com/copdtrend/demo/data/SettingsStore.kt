package com.copdtrend.demo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.copdtrend.demo.domain.DEFAULT_BASE_URL
import com.copdtrend.demo.domain.DEFAULT_USER_ID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "copd_settings")

/**
 * 只存三项：服务器地址、匿名用户编号、演示场景。
 *
 * 这里刻意没有用 Room —— 历史数据由服务端提供（换真模型时客户端不用改数据源），
 * 客户端只需要这几个配置项，DataStore 就够了。
 */
class SettingsStore(private val context: Context, scope: CoroutineScope) {

    private val keyBaseUrl = stringPreferencesKey("base_url")
    private val keyUserId = stringPreferencesKey("user_id")
    private val keyScenario = stringPreferencesKey("demo_scenario")

    val baseUrl: Flow<String> = context.settingsDataStore.data
        .map { it[keyBaseUrl] ?: DEFAULT_BASE_URL }
    val userId: Flow<String> = context.settingsDataStore.data
        .map { it[keyUserId] ?: DEFAULT_USER_ID }
    val scenario: Flow<String> = context.settingsDataStore.data
        .map { it[keyScenario] ?: "" }

    /** 同步快照。ApiClient 需要一个非 suspend 的地址来源。 */
    @Volatile
    var baseUrlSnapshot: String = DEFAULT_BASE_URL
        private set

    @Volatile
    var userIdSnapshot: String = DEFAULT_USER_ID
        private set

    @Volatile
    var scenarioSnapshot: String = ""
        private set

    /**
     * 首次快照是否已经读出来。
     *
     * DataStore 是异步的，冷启动那一瞬间快照还是默认值 —— 如果这时就发请求，
     * 会打到默认地址上、首页先闪一次"未连接"。所以第一次请求前先 await 这个信号。
     */
    private val readySignal = CompletableDeferred<Unit>()

    init {
        scope.launch {
            baseUrlSnapshot = baseUrl.first()
            userIdSnapshot = userId.first()
            scenarioSnapshot = scenario.first()
            readySignal.complete(Unit)
            launch { baseUrl.collect { baseUrlSnapshot = it } }
            launch { userId.collect { userIdSnapshot = it } }
            launch { scenario.collect { scenarioSnapshot = it } }
        }
    }

    /** 等首次快照读完。已经读完时立即返回。 */
    suspend fun awaitReady() = readySignal.await()

    // 写入时同步更新快照：DataStore 的 collect 回来得晚，
    // 而"保存并测试"按钮紧接着就要用新地址发请求。
    suspend fun setBaseUrl(value: String) {
        val v = value.trim()
        baseUrlSnapshot = v
        edit(keyBaseUrl, v)
    }

    suspend fun setUserId(value: String) {
        val v = value.trim().ifBlank { DEFAULT_USER_ID }
        userIdSnapshot = v
        edit(keyUserId, v)
    }

    suspend fun setScenario(value: String) {
        scenarioSnapshot = value
        edit(keyScenario, value)
    }

    private suspend fun edit(key: Preferences.Key<String>, value: String) {
        context.settingsDataStore.edit { it[key] = value }
    }
}
