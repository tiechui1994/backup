package com.example.photobackup.sync

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProvider
import android.content.ContentProviderClient
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.app.Service
import android.content.Intent
import android.content.SyncResult

/** 虚假身份验证器，用于满足 SyncAdapter 的账号需求 */
class StubAuthenticator(context: Context) : AbstractAccountAuthenticator(context) {
    override fun editProperties(response: AccountAuthenticatorResponse?, accountType: String?) = null
    override fun addAccount(response: AccountAuthenticatorResponse?, accountType: String?, authTokenType: String?, requiredFeatures: Array<out String>?, options: Bundle?) = null
    override fun confirmCredentials(response: AccountAuthenticatorResponse?, account: Account?, options: Bundle?) = null
    override fun getAuthToken(response: AccountAuthenticatorResponse?, account: Account?, authTokenType: String?, options: Bundle?) = null
    override fun getAuthTokenLabel(authTokenType: String?) = null
    override fun updateCredentials(response: AccountAuthenticatorResponse?, account: Account?, authTokenType: String?, options: Bundle?) = null
    override fun hasFeatures(response: AccountAuthenticatorResponse?, account: Account?, features: Array<out String>?) = null
}

/** 身份验证器服务，使系统能够与 Authenticator 通信 */
class StubAuthenticatorService : Service() {
    private lateinit var authenticator: StubAuthenticator
    override fun onCreate() { authenticator = StubAuthenticator(this) }
    override fun onBind(intent: Intent?) = authenticator.iBinder
}


/** 虚假同步适配器，系统触发同步时拉活应用 */
class StubSyncAdapter @JvmOverloads constructor(context: Context, autoInitialize: Boolean, allowParallelSyncs: Boolean = false) :
    AbstractThreadedSyncAdapter(context, autoInitialize, allowParallelSyncs) {
    override fun onPerformSync(account: Account?, extras: Bundle?, authority: String?, provider: ContentProviderClient?, syncResult: SyncResult?) {
        Log.d("StubSyncAdapter", "onPerformSync: 系统触发了同步，应用已激活")
    }
}


/** 同步服务，SyncManager 绑定此服务以启动同步 */
class StubSyncService : Service() {
    private companion object {
        private var syncAdapter: StubSyncAdapter? = null
        private val lock = Any()
    }
    override fun onCreate() {
        synchronized(lock) { if (syncAdapter == null) syncAdapter = StubSyncAdapter(applicationContext, true) }
    }
    override fun onBind(intent: Intent?) = syncAdapter?.syncAdapterBinder
}


/** 虚假 ContentProvider，仅用于满足 SyncAdapter 的 authority 需求 */
class StubProvider : ContentProvider() {
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) = null
    override fun getType(uri: Uri) = null
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

