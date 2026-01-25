package com.example.photobackup.sync

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * 身份验证器服务，使系统能够与我们的 Authenticator 通信
 */
class StubAuthenticatorService : Service() {
    private lateinit var authenticator: StubAuthenticator

    override fun onCreate() {
        authenticator = StubAuthenticator(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return authenticator.iBinder
    }
}

