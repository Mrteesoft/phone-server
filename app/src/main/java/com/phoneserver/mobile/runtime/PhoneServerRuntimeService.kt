package com.phoneserver.mobile.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.phoneserver.mobile.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class PhoneServerRuntimeService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        runBlocking(Dispatchers.IO) {
            PhoneServerRuntimeManager.initialize(applicationContext)
        }
        PhoneServerRuntimeManager.markRuntimeServiceActive(true)
        ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PhoneServerRuntimeManager.markRuntimeServiceActive(true)
        return START_STICKY
    }

    override fun onDestroy() {
        PhoneServerRuntimeManager.markRuntimeServiceActive(false)
        runBlocking(Dispatchers.IO) {
            PhoneServerRuntimeManager.close()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.presence_online)
                .setContentTitle(getString(R.string.runtime_notification_title))
                .setContentText(getString(R.string.runtime_notification_body))
                .setOngoing(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.runtime_channel_name),
                NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.runtime_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "phone_server_runtime"
        private const val NOTIFICATION_ID = 1001

        fun ensureRunning(context: Context) {
            val intent = Intent(context, PhoneServerRuntimeService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
