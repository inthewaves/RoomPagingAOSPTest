package com.example.roompagingaosptest.work

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Binder
import android.util.Log
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.roompagingaosptest.MainActivity
import com.example.roompagingaosptest.db.AppInfo
import com.example.roompagingaosptest.db.TestDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class PackageInsertJob(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val input = PackageInsertJobInputData(inputData)
        val pkg = input.packageName
        val newVersion = input.version

        try {
            if (pkg.isBlank()) {
                throw CantMakeException("invalid package name")
            }

            if (newVersion < 1) {
                throw CantMakeException("invalid version --- needs to be positive integer")
            }

            val appInfo = AppInfo(pkg, newVersion, System.currentTimeMillis() / 1000)

            TestDatabase.getInstance(applicationContext)
                .appInfoDao()
                .updateOrInsert(appInfo)
        } catch (e: CantMakeException) {
            return Result.failure(workDataOf(ERROR_MESSAGE_KEY to "Can't insert package: ${e.message}"))
        }

        if (pkg == "install") {
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "Installing appA and appB", Toast.LENGTH_LONG)
                    .show()
                Log.d(TAG, "Installing appA and appB")
            }

            val packageManager = applicationContext.packageManager as PackageManager
            val packageInstaller = packageManager.packageInstaller
            withContext(Dispatchers.IO) {
                val parentSessionId = createSessionAndReturnId(packageInstaller, parentSession = true)
                val parentSession = packageInstaller.openSession(parentSessionId)
                val appASessionId = createSessionAndReturnId(packageInstaller, parentSession = false)
                val appASession = packageInstaller.openSession(appASessionId)

                val appBSessionId = createSessionAndReturnId(packageInstaller, parentSession = false)
                val appBSession = packageInstaller.openSession(appBSessionId)

                val assets = applicationContext.assets
                val files = assets.list("")?.asList()
                Log.d(TAG, "assets has these files: $files")

                appASession.openWrite("appA", 0, -1).use { outputStream ->
                    assets.open("app-release-appA.apk").use {
                        it.copyTo(outputStream)
                    }
                    outputStream.flush()
                    appASession.fsync(outputStream)
                }

                appBSession.openWrite("appB", 0, -1).use { outputStream ->
                    assets.open("app-release-appB.apk").use {
                        it.copyTo(outputStream)
                    }
                    outputStream.flush()
                    appBSession.fsync(outputStream)
                }


                suspendCancellableCoroutine<Unit> { cont ->
                    val broadcastReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            Log.d(TAG, "onReceive(): intent: $intent, calling UID: ${Binder.getCallingUid()}")
                            applicationContext.unregisterReceiver(this)
                            parentSession.close()

                            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Integer.MIN_VALUE)
                            val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                            Log.d(TAG, "status: $status")
                            var statusString: String? = null
                            when (status) {
                                PackageInstaller.STATUS_SUCCESS -> {
                                    statusString = "STATUS_SUCCESS"
                                }
                                PackageInstaller.STATUS_FAILURE -> {
                                    statusString = "STATUS_FAILURE"
                                }
                                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                                    statusString = "STATUS_PENDING_USER_ACTION"
                                    val launchIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                                    launchIntent?.let {
                                        Log.d(TAG, "launching intent")
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        MainActivity.activity?.startActivity(it)
                                    }

                                }
                            }
                            Log.d(TAG, statusString ?: "null status")
                            Toast.makeText(
                                applicationContext,
                                "APK install status: $statusString",
                                Toast.LENGTH_LONG
                            ).show()
                            Log.d(TAG, "onReceive(): intent: status message: $statusMessage")

                            cont.resume(Unit) {}
                        }
                    }
                    parentSession.addChildSessionId(appASessionId)
                    parentSession.addChildSessionId(appBSessionId)
                    val childSessions = parentSession.childSessionIds
                    Log.d(TAG, "isMultiPackage: ${parentSession.isMultiPackage}; childSessions: ${childSessions.asList()}")

                    val action = "com.example.hi"
                    val intentFilter = IntentFilter(action)
                    applicationContext.registerReceiver(broadcastReceiver, intentFilter)

                    Log.d(TAG, "before: calling UID: ${Binder.getCallingUid()}")
                    var success = false
                    try {
                        parentSession.commit(
                            PendingIntent.getBroadcast(
                                applicationContext,
                                applicationContext.packageName.hashCode(),
                                Intent(action),
                                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT
                            ).intentSender
                        )
                        success = true
                    } finally {
                        if (!success) {
                            parentSession.close()
                            packageInstaller.abandonSession(parentSessionId)
                        }
                    }
                }
                try {
                    packageInstaller.abandonSession(parentSessionId)
                } catch (e: SecurityException) {
                    Log.e(TAG, "security exception trying to abandon session", e)
                }

            }
        }

        return Result.success()
    }

    private suspend fun createSessionAndReturnId(packageInstaller: PackageInstaller, parentSession: Boolean): Int {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply {
                if (parentSession) {
                    setMultiPackage()
                }
                setInstallReason(PackageManager.INSTALL_REASON_USER)
            }


        return withContext(Dispatchers.IO) {
            packageInstaller.createSession(params)
        }
    }

    private class CantMakeException(message: String) : Exception(message)

    companion object {
        private const val TAG = "PackageInsertJob"
        const val ERROR_MESSAGE_KEY = "error"
        fun createTag(data: PackageInsertJobInputData) = "insertjob-${data.packageName}"
    }
}

inline class PackageInsertJobInputData(val data: Data) {
    val packageName: String
        get() = data.getString(PACKAGE_NAME_KEY)!!
    val version: Int
        get() = data.getInt(VERSION_KEY, -1)

    companion object {
        private const val PACKAGE_NAME_KEY = "package"
        private const val VERSION_KEY = "version"
        fun create(packageName: String, version: Int?) =
            PackageInsertJobInputData(
                workDataOf(PACKAGE_NAME_KEY to packageName, VERSION_KEY to version)
            )
    }
}