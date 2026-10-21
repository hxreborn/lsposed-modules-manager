package com.rushi.modulesmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rushi.modulesmanager.ui.AppRoot
import com.rushi.modulesmanager.ui.theme.ModulesManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ModulesManagerApplication).container
        setContent {
            ModulesManagerTheme {
                val viewModel: AppViewModel = viewModel(factory = AppViewModel.factory(container))
                AppRoot(viewModel)
            }
        }
    }
}
