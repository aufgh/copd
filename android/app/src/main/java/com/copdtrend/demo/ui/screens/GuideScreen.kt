package com.copdtrend.demo.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.copdtrend.demo.ui.MainViewModel
import com.copdtrend.demo.ui.components.PrimaryButton
import com.copdtrend.demo.ui.components.ScreenScaffold
import com.copdtrend.demo.ui.components.SectionCard
import com.copdtrend.demo.ui.theme.Ink900
import com.copdtrend.demo.ui.theme.Mist100
import com.copdtrend.demo.ui.theme.Mist50
import com.copdtrend.demo.ui.theme.Mist70
import com.copdtrend.demo.ui.theme.Teal

/**
 * 录音说明页。时长要求全部来自服务端下发的 audio_requirements，
 * 服务端调整要求时这一页自动跟着变，不需要改代码。
 */
@Composable
fun GuideScreen(vm: MainViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val state by vm.state.collectAsState()
    val req = state.health?.audioRequirements
    val breathSec = (req?.breathingTargetMs ?: 20_000) / 1000
    val coughSec = (req?.coughTargetMs ?: 8_000) / 1000

    ScreenScaffold(
        title = "录音说明",
        onBack = onBack,
        bottomBar = {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                PrimaryButton(text = "我准备好了", onClick = onNext)
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = "两段录音，大约一分钟",
                style = MaterialTheme.typography.headlineSmall,
                color = Mist100,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "先录平静呼吸，再录咳嗽。全程在本地和你自己的电脑之间传输，不经过互联网。",
                style = MaterialTheme.typography.bodyLarge,
                color = Mist70,
            )
            Spacer(Modifier.height(20.dp))

            SectionCard {
                GuideStep(
                    index = 1,
                    title = "平静呼吸 $breathSec 秒",
                    detail = "手机距口鼻约 15 厘米，跟着屏幕上的圆圈，缓慢吸气、缓慢呼气，不要刻意加深。",
                )
                Spacer(Modifier.height(16.dp))
                GuideStep(
                    index = 2,
                    title = "自然咳嗽 3 声",
                    detail = "在 $coughSec 秒内咳 3 声，每声之间停顿一下。不用用力，像平时清嗓子那样即可。",
                )
            }
            Spacer(Modifier.height(16.dp))

            SectionCard {
                Text("录之前检查一下", style = MaterialTheme.typography.titleSmall, color = Mist100)
                Spacer(Modifier.height(10.dp))
                GuideTip("环境安静，关掉电视和风扇")
                GuideTip("不要用手指挡住手机底部的麦克风")
                GuideTip("不要戴蓝牙耳机，用手机自带麦克风")
                GuideTip("每天在同一时间录，趋势才有可比性")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GuideStep(index: Int, title: String, detail: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Teal),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Ink900,
            )
        }
        Spacer(Modifier.size(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Mist100)
            Spacer(Modifier.height(4.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Mist70)
        }
    }
}

@Composable
private fun GuideTip(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("·", style = MaterialTheme.typography.bodyLarge, color = Mist50)
        Spacer(Modifier.size(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Mist70)
    }
}
