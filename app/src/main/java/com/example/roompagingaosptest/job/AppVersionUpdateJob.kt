package com.example.roompagingaosptest.job

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PersistableBundle
import android.util.Log
import androidx.core.os.persistableBundleOf
import androidx.room.withTransaction
import com.example.roompagingaosptest.db.AppInfo
import com.example.roompagingaosptest.db.ProgressDatabase
import com.example.roompagingaosptest.db.TestDatabase
import com.example.roompagingaosptest.job.jobchain.ChainingJobService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class AppVersionUpdateJobService : ChainingJobService() {
    companion object {
        private const val TAG = "AppVersionUpdateJobService"
        const val JOB_ID = 20000000

        private fun deriveJobId(packageName: String) = packageName.hashCode()

        fun createName(appInfo: AppInfo) = "AppVersionUpdateJob-${appInfo.packageName}"

        fun enqueueJob(context: Context, packageName: String) {
            val jobInfo = JobInfo.Builder(
                deriveJobId(packageName),
                ComponentName(context, AppVersionUpdateJobService::class.java)
            ).setExtras(Input(packageName).extras)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build()
            val jobScheduler = context.getSystemService(JobScheduler::class.java)
            jobScheduler.schedule(jobInfo).also { result ->
                if (result == JobScheduler.RESULT_FAILURE) {
                    Log.d(TAG, "enqueueJob(): RESULT_FAILURE ($result) for $packageName")
                } else {
                    Log.d(TAG, "enqueueJob(): RESULT_SUCCESS ($result) for $packageName")
                }

                Log.d(TAG, "pending: ${jobScheduler.allPendingJobs}")
            }
        }
        fun cancelJob(context: Context, packageName: String) {
            context.getSystemService(JobScheduler::class.java).cancel(deriveJobId(packageName))
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind(): $intent with ${intent?.extras}, key set ${intent?.extras?.keySet()}")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy()")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.d(TAG, "onLowMemory()")
    }

    override val dispatcher: CoroutineDispatcher
        get() = Dispatchers.Default

    override suspend fun runJob(params: JobParameters): JobResult = coroutineScope {
        val packageManager = applicationContext.packageManager
        val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val systemSharedLibs = packageManager.systemSharedLibraryNames
        val sharedLibs = packageManager.getSharedLibraries(PackageManager.GET_META_DATA)

        withContext(Dispatchers.IO) {
            Log.d("AppVersionUpdateJob", "PACKAGES: ${packages.joinToString("\n")}")
            Log.d("AppVersionUpdateJob", "SYSTEMSHAREDLIBS: ${systemSharedLibs?.asList()?.joinToString("\n")}")
            Log.d("AppVersionUpdateJob", "SHAREDLIBS: ${sharedLibs.joinToString("\n")}")
        }

        val graphene = packages.filter {
            it.packageName.contains("graphene") ||
                    it.packageName.contains("example")
        }
        withContext(Dispatchers.IO) {
            val apkDirs = graphene.map { it.sourceDir }
            Log.d("AppVersionUpdateJob", "graphene: $graphene\n " +
                    "apk dirs: $apkDirs")

            Log.d("AppVersionUpdateJob", "can read apk dirs? ${apkDirs.map { File(it).canRead() }}")
        }

        val database = TestDatabase.getInstance(applicationContext)
        val appUpdateProgressDao = ProgressDatabase.getInstance(applicationContext)
            .appUpdateProgressDao()
        val input = Input(params.extras)

        withContext(Dispatchers.IO) {
            Log.d(TAG, "Processing ${input.pkg}")
        }

        var percentage = 0.0
        // setProgress(Progress(0.0).progressData)
        appUpdateProgressDao.upsertProgressForPackage(input.pkg, 0.0)
        repeat(10 * 33) {
            delay(12L)
            percentage += 0.002
            appUpdateProgressDao.upsertProgressForPackage(input.pkg, percentage)
        }
        appUpdateProgressDao.upsertProgressForPackage(input.pkg, 2/3.0)

        val success = coroutineScope {
            val success = database.withTransaction {
                val dao = database.appInfoDao()
                val appInfo = dao.getAppInfo(input.pkg) ?: return@withTransaction false
                dao.updateVersionCode(appInfo.packageName, appInfo.versionCode + 1L)
                appUpdateProgressDao.upsertProgressForPackage(input.pkg, 0.9)
                true
            }
            success
        }
        delay(2500L)
        appUpdateProgressDao.upsertProgressForPackage(input.pkg, 1.0)
        withContext(Dispatchers.Main) {
            Log.d(TAG, "${input.pkg} has reached 100%")
        }
        delay(2500L)

        appUpdateProgressDao.deletePackage(input.pkg)

        return@coroutineScope JobResult.Success()
    }

    override fun createNextJobInfo(params: JobParameters, result: JobResult.Success): JobInfo? {
        return JobInfo.Builder(
            params.jobId + 1,
            ComponentName(this, UselessJobService::class.java)
        ).build()
    }

    override fun onStopJobInner(params: JobParameters): Boolean {
        Log.d(TAG, "onStopJobInner, params: $params, id: ${params.jobId}")
        return true
    }

    class Input constructor(val extras: PersistableBundle) {
        val pkg: String get() = extras.getString(PACKAGE_KEY)!!
        constructor(
            packageName: String
        ): this(persistableBundleOf(PACKAGE_KEY to packageName))

        companion object {
            private const val PACKAGE_KEY = "package"
        }
    }
}

