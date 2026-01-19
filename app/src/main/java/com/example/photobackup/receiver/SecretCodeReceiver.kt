package com.example.photobackup.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.photobackup.MainActivity

/**
 * 接收拨号盘暗号 (*#*#1234#*#*) 的广播接收器
 */
class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SECRET_CODE") {
            val startIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(startIntent)
        }
    }
}

