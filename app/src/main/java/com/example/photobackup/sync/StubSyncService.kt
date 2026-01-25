package com.example.photobackup.sync

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * 同步服务，SyncManager 会绑定这个服务来启动同步
 */
class StubSyncService : Service() {
    private companion object {
        private var syncAdapter: StubSyncAdapter? = null
        private val lock = Any()
    }

    override fun onCreate() {
        synchronized(lock) {
            if (syncAdapter == null) {
                syncAdapter = StubSyncAdapter(applicationContext, true)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return syncAdapter?.syncAdapterBinder
    }
}

