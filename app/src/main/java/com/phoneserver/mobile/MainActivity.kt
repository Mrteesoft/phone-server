package com.phoneserver.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneserver.mobile.runtime.PhoneServerRuntimeService
import com.phoneserver.mobile.ui.PhoneServerApp
import com.phoneserver.mobile.ui.PhoneServerViewModel
import com.phoneserver.mobile.ui.theme.PhoneServerTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            PhoneServerRuntimeService.ensureRunning(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        startRuntimeServiceIfAllowed()

        setContent {
            val viewModel: PhoneServerViewModel = viewModel()

            PhoneServerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PhoneServerApp(viewModel = viewModel)
                }
            }
        }
    }

    private fun startRuntimeServiceIfAllowed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            PhoneServerRuntimeService.ensureRunning(this)
            return
        }

        val hasNotificationPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasNotificationPermission) {
            PhoneServerRuntimeService.ensureRunning(this)
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
