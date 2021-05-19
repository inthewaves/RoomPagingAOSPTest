package com.example.roompagingaosptest.work

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.roompagingaosptest.db.AppInfo
import com.example.roompagingaosptest.db.ProgressDatabase
import com.example.roompagingaosptest.db.TestDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class AppVersionUpdateJob(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    companion object {
        const val WORK_TAG = "AppVersionUpdateJob"
        fun createName(appInfo: AppInfo) =
            "AppVersionUpdateJob-${appInfo.packageName}"
    }

    override suspend fun doWork(): Result {
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
        val input = Input(inputData)
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
            Log.d(WORK_TAG, "${input.pkg} has reached 100%")
        }
        delay(2500L)

        appUpdateProgressDao.deletePackage(input.pkg)
        return if (success) Result.success() else Result.failure()
    }

    class Input constructor(val inputData: Data) {
        val pkg: String
            get() = inputData.getString(PACKAGE_KEY)!!
        constructor(appInfo: AppInfo): this(workDataOf(PACKAGE_KEY to appInfo.packageName))

        companion object {
            private const val PACKAGE_KEY = "package"
        }
    }
}

