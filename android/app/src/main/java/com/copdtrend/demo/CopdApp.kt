package com.copdtrend.demo

import android.app.Application
import com.copdtrend.demo.data.AssessmentRepository
import com.copdtrend.demo.data.SettingsStore
import com.copdtrend.demo.network.ApiClient
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * 手写依赖容器。
 *
 * 只有三个单例，引入 Hilt 的收益不如它带来的构建复杂度成本，
 * 所以这里直接在 Application 里持有。
 */
class CopdApp : Application() {

    /** 与进程同生命周期，用于 SettingsStore 的快照收集。 */
    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob()) }

    lateinit var settings: SettingsStore
        private set

    lateinit var api: ApiClient
        private set

    lateinit var repository: AssessmentRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this, appScope)
        // ApiClient 每次请求时读快照，用户改地址后不需要重建客户端
        api = ApiClient { settings.baseUrlSnapshot }
        repository = AssessmentRepository(
            api = api,
            settings = settings,
            audioDir = File(cacheDir, "recordings"),
        )
    }
}
