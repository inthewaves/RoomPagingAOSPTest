package com.example.roompagingaosptest.job

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobWorkItem
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Binder
import android.util.Log
import android.widget.Toast
import com.example.roompagingaosptest.MainActivity
import com.example.roompagingaosptest.db.AppInfo
import com.example.roompagingaosptest.db.TestDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class PackageInsertJobService : CoroutineJobService() {
    companion object {
        private const val TAG = "PackageInsertJobService"
        private const val JOB_ID = 10000000
        private const val EXTRA_PACKAGE_NAME = "package"
        private const val EXTRA_VERSION = "version"

        fun enqueueJob(context: Context, packageName: String, version: Int) {
            val jobInfo = JobInfo.Builder(
                JOB_ID /* use a single jobId to enforce serial execution */,
                ComponentName(context, PackageInsertJobService::class.java)
            ).build()

            val jobWorkItem = JobWorkItem(
                Intent()
                    .putExtra(EXTRA_PACKAGE_NAME, packageName)
                    .putExtra(EXTRA_VERSION, version)
            )

            context.getSystemService(JobScheduler::class.java).enqueue(jobInfo, jobWorkItem)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy(), from location")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind(): $intent with ${intent?.extras}, key set ${intent?.extras?.keySet()}")
        return super.onUnbind(intent)
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
    
    override val dispatcher: CoroutineDispatcher
        get() = Dispatchers.IO

    override suspend fun doWork(params: JobParameters) = coroutineScope {
        params.forEachWork { workItem ->
            ensureActive()
            val pkg = workItem.intent.getStringExtra(EXTRA_PACKAGE_NAME)!!
            val newVersion = workItem.intent.getIntExtra(EXTRA_VERSION, -1)
            Log.d(TAG, "doWork(): processing $pkg, newVersion: $newVersion")
            if (newVersion < 0) {
                return@forEachWork
            }

            Log.d(TAG, "delaying for 3 seconds")
            delay(3000L)

            val appInfo = AppInfo(pkg, newVersion, System.currentTimeMillis() / 1000)

            TestDatabase.getInstance(applicationContext)
                .appInfoDao()
                .updateOrInsert(appInfo)

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
                        cont.invokeOnCancellation {
                            Log.d(TAG, "invokeOnCancellation(): unregistering receiver")
                            application.unregisterReceiver(broadcastReceiver)
                        }

                        Log.d(TAG, "before: calling UID: ${Binder.getCallingUid()}")
                        var success = false
                        try {
                            parentSession.commit(
                                PendingIntent.getBroadcast(
                                    applicationContext,
                                    applicationContext.packageName.hashCode(),
                                    Intent(action).setPackage(applicationContext.packageName),
                                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
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
        }
    }

    override fun onStopJobInner(params: JobParameters): Boolean {
        Log.d(TAG, "onStopJobInner, params: $params, id: ${params.jobId}")
        return true
    }
}