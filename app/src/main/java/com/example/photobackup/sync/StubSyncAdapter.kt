package com.example.photobackup.sync

import android.accounts.Account
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import android.util.Log

/**
 * 虚假同步适配器
 * 当系统触发同步时，该类的 onPerformSync 会被调用，从而拉活应用进程
 */
class StubSyncAdapter @JvmOverloads constructor(
    context: Context,
    autoInitialize: Boolean,
    allowParallelSyncs: Boolean = false
) : AbstractThreadedSyncAdapter(context, autoInitialize, allowParallelSyncs) {

    override fun onPerformSync(
        account: Account?,
        extras: Bundle?,
        authority: String?,
        provider: ContentProvider?,
        syncResult: SyncResult?
    ) {
        Log.d("StubSyncAdapter", "onPerformSync: 系统触发了同步，应用已激活")
        
        // 这里可以视情况触发一些轻量级的检查，例如检查备份服务是否在运行
        // 但不要在这里做耗时操作，SyncAdapter 的主要目的是拉活
    }
}

