// Copyright (C) 2025 Innixunix

package io.github.innixunix.deltapatcher

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

class NotificationService : Service() {
    private val binder = LocalBinder()
    
    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "delta_patcher_channel"
        const val CHANNEL_NAME = "Delta Patcher"
        
        fun dismissNotification(context: Context) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.cancel(NOTIFICATION_ID)
        }
        
        @RequiresApi(Build.VERSION_CODES.O)
        fun startService(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val intent = Intent(context, NotificationService::class.java)
                    context.startForegroundService(intent)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val intent = Intent(context, NotificationService::class.java)
                    context.startForegroundService(intent)
                } else {
                    val intent = Intent(context, NotificationService::class.java)
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Fallback: just start as regular service if foreground fails
                try {
                    val intent = Intent(context, NotificationService::class.java)
                    context.startService(intent)
                } catch (e2: Exception) {
                    // Ignore
                }
            }
        }
        
        fun stopService(context: Context) {
            try {
                val intent = Intent(context, NotificationService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    
    inner class LocalBinder : Binder() {
        fun getService(): NotificationService = this@NotificationService
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "EXIT_APP") {
            exitApp()
            return START_NOT_STICKY
        }
        
        try {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // if we can't start as foreground service, create a regular notification
            // this can happen on newer Android versions with stricter restrictions
            try {
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID, createNotification())
            } catch (e2: Exception) {
                // If even regular notification fails, just run as background service
            }
        }
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            stopForeground(true)
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.cancel(NOTIFICATION_ID)
            
            FileUtil.clearCache(this)
        } catch (e: Exception) {
            FileUtil.clearCache(this)
        }
    }
    
    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Delta Patcher is running in the background"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val exitIntent = Intent(this, NotificationService::class.java).apply {
            action = "EXIT_APP"
        }
        val exitPendingIntent = PendingIntent.getService(
            this, 1, exitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Delta Patcher")
            .setContentText("is running...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Exit",
                exitPendingIntent
            )
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    fun updateNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Delta Patcher")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun exitApp() {
        FileUtil.clearCache(this)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.cancel(NOTIFICATION_ID)
        stopSelf()
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(0)
    }
}
