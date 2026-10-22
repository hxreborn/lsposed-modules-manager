package com.rushi.modulesmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rushi.modulesmanager.data.AppTheme
import com.rushi.modulesmanager.ui.AppRoot
import com.rushi.modulesmanager.ui.theme.ModulesManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ModulesManagerApplication).container
        setContent {
            val systemDark = isSystemInDarkTheme()
            val theme by container.settings.theme.collectAsStateWithLifecycle()
            val amoled by container.settings.amoled.collectAsStateWithLifecycle()
            val systemColors by container.settings.systemColors.collectAsStateWithLifecycle()
            val darkTheme = when (theme) {
                AppTheme.SYSTEM -> systemDark
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }
            ModulesManagerTheme(
                darkTheme = darkTheme,
                dynamicColor = systemColors,
                amoled = amoled,
            ) {
                val viewModel: AppViewModel = viewModel(factory = AppViewModel.factory(container))
                AppRoot(viewModel)
            }
        }
    }
}
