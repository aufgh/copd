package com.copdtrend.demo.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.copdtrend.demo.domain.RecordingStep
import com.copdtrend.demo.ui.screens.AnalyzingScreen
import com.copdtrend.demo.ui.screens.GuideScreen
import com.copdtrend.demo.ui.screens.HistoryScreen
import com.copdtrend.demo.ui.screens.HomeScreen
import com.copdtrend.demo.ui.screens.RecordScreen
import com.copdtrend.demo.ui.screens.ResultScreen
import com.copdtrend.demo.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val GUIDE = "guide"
    const val RECORD_BREATHING = "record_breathing"
    const val RECORD_COUGH = "record_cough"
    const val ANALYZING = "analyzing"
    const val RESULT = "result"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

/**
 * 导航图。八个页面，一个 Activity 级 ViewModel。
 *
 * 录音结果（文件 + 质检结论）放在 ViewModel 里跨页面共享，
 * 导航参数只传路由，不传数据 —— 进程被系统回收后不会拿到半截状态。
 */
@Composable
fun AppNav(vm: MainViewModel) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                vm = vm,
                onStart = {
                    vm.resetSession()
                    nav.navigate(Routes.GUIDE)
                },
                onHistory = { nav.navigate(Routes.HISTORY) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.GUIDE) {
            GuideScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onNext = { nav.navigate(Routes.RECORD_BREATHING) },
            )
        }

        composable(Routes.RECORD_BREATHING) {
            RecordScreen(
                vm = vm,
                step = RecordingStep.BREATHING,
                onBack = { nav.popBackStack() },
                onNext = { nav.navigate(Routes.RECORD_COUGH) },
            )
        }

        composable(Routes.RECORD_COUGH) {
            RecordScreen(
                vm = vm,
                step = RecordingStep.COUGH,
                onBack = { nav.popBackStack() },
                onNext = {
                    vm.startAnalysis()
                    nav.navigate(Routes.ANALYZING)
                },
            )
        }

        composable(Routes.ANALYZING) {
            AnalyzingScreen(
                vm = vm,
                onDone = {
                    nav.navigate(Routes.RESULT) {
                        popUpTo(Routes.HOME)
                    }
                },
                onGiveUp = {
                    nav.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }

        composable(Routes.RESULT) {
            ResultScreen(
                vm = vm,
                onHome = { nav.popBackStack(Routes.HOME, inclusive = false) },
                onHistory = { nav.navigate(Routes.HISTORY) },
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(vm = vm, onBack = { nav.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
