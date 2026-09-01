package com.copdtrend.demo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.copdtrend.demo.network.ApiClient
import com.copdtrend.demo.ui.MainViewModel
import com.copdtrend.demo.ui.components.GhostButton
import com.copdtrend.demo.ui.components.MetricRow
import com.copdtrend.demo.ui.components.NoticeCard
import com.copdtrend.demo.ui.components.Pill
import com.copdtrend.demo.ui.components.ScreenScaffold
import com.copdtrend.demo.ui.components.SectionCard
import com.copdtrend.demo.ui.theme.Ink600
import com.copdtrend.demo.ui.theme.Ink900
import com.copdtrend.demo.ui.theme.Mist100
import com.copdtrend.demo.ui.theme.Mist50
import com.copdtrend.demo.ui.theme.Mist70
import com.copdtrend.demo.ui.theme.RiskRecheck
import com.copdtrend.demo.ui.theme.RiskRecheckSoft
import com.copdtrend.demo.ui.theme.Teal
import com.copdtrend.demo.ui.theme.TealGlow

/**
 * 设置页。演示当天真正会被用到的三项：服务器地址、匿名编号、演示场景。
 *
 * 场景选择只在服务端 engine == "demo" 时出现 ——
 * 接入真模型后 /health 不再返回 available_scenarios，这一块自动消失，
 * 不需要改客户端代码。
 */
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    var urlInput by remember(state.baseUrl) { mutableStateOf(state.baseUrl) }
    var idInput by remember(state.userId) { mutableStateOf(state.userId) }
    val urlValid = ApiClient.isValidBaseUrl(urlInput)

    ScreenScaffold(title = "设置", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            SectionCard {
                Text("分析服务地址", style = MaterialTheme.typography.titleMedium, color = Mist100)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "填电脑的局域网地址，例如 http://192.168.1.100:8000。只填 IP 也可以，会自动补全。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist50,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = urlInput.isNotBlank() && !urlValid,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    GhostButton(
                        text = "保存并测试",
                        modifier = Modifier.weight(1f),
                        enabled = urlValid,
                        onClick = {
                            // 存清洗过的地址：输入法带进来的全角字符和不可见空白
                            // 不会被原样存下来，否则地址看着对却永远连不上。
                            val canonical = ApiClient.canonicalBaseUrl(urlInput) ?: urlInput
                            urlInput = canonical
                            vm.setBaseUrl(canonical)
                        },
                    )
                    Spacer(Modifier.size(10.dp))
                    GhostButton(
                        text = "重新连接",
                        modifier = Modifier.weight(1f),
                        onClick = { vm.refreshHealth() },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            val health = state.health
            if (health != null) {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "连接正常",
                            style = MaterialTheme.typography.titleMedium,
                            color = Mist100,
                            modifier = Modifier.weight(1f),
                        )
                        Pill(text = health.engine, color = Teal, container = TealGlow)
                    }
                    Spacer(Modifier.height(6.dp))
                    MetricRow(label = "接口版本", value = health.schemaVersion)
                    MetricRow(
                        label = "引擎版本",
                        value = health.modelVersion.ifBlank { "—" },
                    )
                    MetricRow(
                        label = "录音要求",
                        value = "${health.audioRequirements.sampleRate / 1000} kHz",
                        hint = "呼吸 ${health.audioRequirements.breathingTargetMs / 1000} 秒 · " +
                            "咳嗽 ${health.audioRequirements.coughTargetMs / 1000} 秒",
                    )
                }
            } else {
                NoticeCard(
                    title = "还没有连上分析服务",
                    message = buildString {
                        val f = state.healthFailure
                        append(
                            f?.hint?.takeIf { it.isNotBlank() }
                                ?: f?.message?.takeIf { it.isNotBlank() }
                                ?: "请确认电脑上的服务已启动，并且手机和电脑连在同一个 Wi-Fi。"
                        )
                        append("\n实际请求地址：").append(state.baseUrl)
                        f?.code?.takeIf { it.isNotBlank() }?.let { append("  [").append(it).append(']') }
                    },
                    accent = RiskRecheck,
                    container = RiskRecheckSoft,
                    actionText = "重试",
                    onAction = { vm.refreshHealth() },
                )
            }
            Spacer(Modifier.height(16.dp))

            SectionCard {
                Text("匿名编号", style = MaterialTheme.typography.titleMedium, color = Mist100)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "只用于把历史记录归到一起，不要填真实姓名。上传的文件也只用随机检测编号命名。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist50,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = idInput,
                    onValueChange = { idInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(12.dp))
                GhostButton(
                    text = "保存编号",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = idInput.isNotBlank(),
                    onClick = { vm.setUserId(idInput) },
                )
            }
            Spacer(Modifier.height(16.dp))

            val scenarios = state.health?.availableScenarios.orEmpty()
            if (state.isDemoEngine && scenarios.isNotEmpty()) {
                SectionCard {
                    Text("演示场景", style = MaterialTheme.typography.titleMedium, color = Mist100)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "同一个编号、同一天、同一个场景，结果是可重复的 —— 演示时前后不会打脸。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Mist50,
                    )
                    Spacer(Modifier.height(12.dp))
                    ScenarioOption(
                        label = "跟随服务端默认",
                        selected = state.scenario.isBlank(),
                        onClick = { vm.setScenario("") },
                    )
                    scenarios.forEach { item ->
                        ScenarioOption(
                            label = item.label.ifBlank { item.key },
                            selected = state.scenario == item.key,
                            onClick = { vm.setScenario(item.key) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 单选行。用勾选圆点 + 文字，触控区域整行 48 dp。 */
@Composable
private fun ScenarioOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (selected) Teal else Ink900)
                .border(1.dp, if (selected) Teal else Ink600, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text("✓", style = MaterialTheme.typography.labelSmall, color = Ink900)
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) Mist100 else Mist70,
        )
    }
}
