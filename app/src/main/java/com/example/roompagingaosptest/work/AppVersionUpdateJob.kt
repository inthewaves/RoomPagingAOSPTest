package com.example.roompagingaosptest.work

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.PersistableBundle
import android.util.Log
import androidx.room.withTransaction
import com.example.roompagingaosptest.db.AppInfo
import com.example.roompagingaosptest.db.ProgressDatabase
import com.example.roompagingaosptest.db.TestDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class AppVersionUpdateJobService : CoroutineJobService() {
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
                .build()
            context.getSystemService(JobScheduler::class.java).schedule(jobInfo)
        }
        fun cancelJob(context: Context, packageName: String) {
            context.getSystemService(JobScheduler::class.java).cancel(deriveJobId(packageName))
        }
    }

    override val dispatcher: CoroutineDispatcher
        get() = Dispatchers.Default

    override suspend fun doWork(params: JobParameters) = coroutineScope {
        val packageManager = applicationContext.packageManager
        val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        withContext(Dispatchers.Main) {
            Log.d("AppVersionUpdateJob", "packages: ${packages.toList().joinToString("\n")}")
        }

        val graphene = packages.filter {
            it.packageName.contains("graphene") ||
                    it.packageName.contains("example")
        }
        withContext(Dispatchers.Main) {
            val apkDirs = graphene.map { it.sourceDir }
            Log.d("AppVersionUpdateJob", "graphene: $graphene\n " +
                    "apk dirs: $apkDirs")

            Log.d("AppVersionUpdateJob", "can read apk dirs? ${apkDirs.map { File(it).canRead() }}")
        }

        val database = TestDatabase.getInstance(applicationContext)
        val appUpdateProgressDao = ProgressDatabase.getInstance(applicationContext)
            .appUpdateProgressDao()
        val input = Input(params.extras)
        var percentage: Double = 0.0
        // setProgress(Progress(0.0).progressData)
        appUpdateProgressDao.updateProgressForPackage(input.pkg, 0.0)
        repeat(10 * 33) {
            delay(50L)
            percentage += 0.002
            appUpdateProgressDao.updateProgressForPackage(input.pkg, percentage)
        }
        appUpdateProgressDao.updateProgressForPackage(input.pkg, 2/3.0)

        val success = coroutineScope {
            val success = database.withTransaction {
                val dao = database.appInfoDao()
                val appInfo = dao.getAppInfo(input.pkg) ?: return@withTransaction false
                dao.updateVersionCode(appInfo.packageName, appInfo.versionCode + 1L)
                appUpdateProgressDao.updateProgressForPackage(input.pkg, 0.9)
                true
            }
            success
        }
        delay(2500L)
        appUpdateProgressDao.updateProgressForPackage(input.pkg, 1.0)
        withContext(Dispatchers.Main) {
            Log.d(TAG, "${input.pkg} has reached 100%")
        }
        delay(2500L)

        appUpdateProgressDao.deletePackage(input.pkg)

        jobFinished(params, false)
    }

    override fun onStopJobInner(params: JobParameters): Boolean {
        return false
    }

    class Input constructor(val extras: PersistableBundle) {
        val pkg: String get() = extras.getString(PACKAGE_KEY)!!
        constructor(packageName: String): this(
            PersistableBundle(1).apply { putString(PACKAGE_KEY, packageName) }
        )

        companion object {
            private const val PACKAGE_KEY = "package"
        }
    }
}

