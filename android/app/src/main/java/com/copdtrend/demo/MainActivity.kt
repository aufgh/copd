package com.copdtrend.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.copdtrend.demo.ui.AppNav
import com.copdtrend.demo.ui.MainViewModel
import com.copdtrend.demo.ui.theme.CopdTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application as CopdApp)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CopdTheme {
                AppNav(vm = viewModel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // 息屏或切后台时停掉试听，避免声音在后台继续播
        viewModel.player.stop()
    }
}
