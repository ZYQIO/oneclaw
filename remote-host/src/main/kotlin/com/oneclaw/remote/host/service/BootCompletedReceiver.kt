package com.oneclaw.remote.host.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.oneclaw.remote.host.storage.HostPreferencesStore

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }
        if (!HostPreferencesStore(context).autoStartEnabled()) {
            return
        }
        ContextCompat.startForegroundService(
            context,
            Intent(context, RemoteHostForegroundService::class.java).setAction(RemoteHostForegroundService.ACTION_START)
        )
    }
}
