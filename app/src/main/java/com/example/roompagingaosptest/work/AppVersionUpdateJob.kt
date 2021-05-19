package com.example.roompagingaosptest.work

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.collection.arrayMapOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.roompagingaosptest.db.AppInfo
import com.example.roompagingaosptest.db.ProgressDatabase
import com.example.roompagingaosptest.db.TestDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
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
        val updaterWatcher = UpdaterWatcher.getInstance(applicationContext)
        val progress = updaterWatcher.getOrCreateProgressForPackage(input.pkg) as MutableLiveData<WorkerProgress>
        var percentage: Double = 0.0
        // setProgress(Progress(0.0).progressData)
        appUpdateProgressDao.updateProgressForPackage(input.pkg, 0.0)
        progress.postValue(WorkerProgress.ZERO)
        repeat(10 * 33) {
            delay(50L)
            percentage += 0.002
            // setProgress(Progress(percentage).progressData)
            progress.percentage = percentage
            appUpdateProgressDao.updateProgressForPackage(input.pkg, percentage)
        }
        // setProgress(Progress(2/3.0).progressData)
        progress.percentage = 2/3.0
        appUpdateProgressDao.updateProgressForPackage(input.pkg, 2/3.0)
        delay(2500L)

        val success =  coroutineScope {
            val progressActor = actor<Double>(capacity = Channel.CONFLATED) {
                for (percentage in channel) {
                    appUpdateProgressDao.updateProgressForPackage(input.pkg, percentage)
                }
            }

            val success = database.withTransaction {
                val dao = database.appInfoDao()
                val appInfo = dao.getAppInfo(input.pkg) ?: return@withTransaction false
                dao.updateVersionCode(appInfo.packageName, appInfo.versionCode + 1L)
                // setProgress(Progress(0.9).progressData)
                progress.percentage = 0.9
                progressActor.offer(0.9)
                true
            }
            progressActor.close()

            success
        }
        delay(2500L)
        // setProgress(Progress(1.0).progressData)
        progress.postValue(WorkerProgress.FINISHED)
        appUpdateProgressDao.updateProgressForPackage(input.pkg, 1.0)
        withContext(Dispatchers.Main) {
            Log.d(WORK_TAG, "${input.pkg} has reached 100%")
        }
        delay(2500L)

        updaterWatcher.removePackage(input.pkg)
        appUpdateProgressDao.deletePackage(input.pkg)
        return if (success) Result.success() else Result.failure()
    }

    class UpdaterWatcher private constructor(context: Context) {
        private val mapMutex = Mutex()
        private val appInfoMap: MutableMap<String, LiveData<WorkerProgress>> = arrayMapOf()
        private val workManager = WorkManager.getInstance(context)

        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
        fun removePackage(pkg: String) {
            synchronized(appInfoMap) { appInfoMap.remove(pkg) }
        }

        fun removePackageIfComplete(pkg: String) {
            synchronized(appInfoMap) {
                if (appInfoMap[pkg]?.value == WorkerProgress.FINISHED) {
                    appInfoMap.remove(pkg)
                }
            }
        }

        fun getProgressForPackageOrNull(pkg: String): LiveData<WorkerProgress>? =
            synchronized(appInfoMap) {
                appInfoMap[pkg]
            }

        fun getOrCreateProgressForPackage(pkg: String): LiveData<WorkerProgress> =
            synchronized(appInfoMap) {
                appInfoMap.getOrPut(
                    pkg,
                    { MutableLiveData(WorkerProgress.FINISHED) }
                )
            }

        companion object {
            private var instance: UpdaterWatcher? = null
            fun getInstance(context: Context) =
                instance ?: synchronized(UpdaterWatcher::class.java) {
                    UpdaterWatcher(context).also { instance = it }
                }
        }
    }

    class Input constructor(val inputData: Data) {
        val pkg: String
            get() = inputData.getString(PACKAGE_KEY)!!
        constructor(appInfo: AppInfo): this(workDataOf(PACKAGE_KEY to appInfo.packageName))

        companion object {
            private const val PACKAGE_KEY = "package"
        }
    }

    class Progress constructor(val progressData: Data) {
        constructor(percentage: Double): this(workDataOf(PROGRESS_PERCENT_KEY to percentage))

        val percentage: Double
            get() = progressData.getDouble(PROGRESS_PERCENT_KEY, 0.0)

        companion object {
            const val PROGRESS_PERCENT_KEY = "percent"
        }
    }
}

inline class WorkerProgress(val percentage: Double) {
    val isFinished: Boolean
        get() = this == FINISHED || percentage == -1.0

    operator fun plus(other: WorkerProgress) = WorkerProgress(percentage + other.percentage)

    companion object {
        val ZERO = WorkerProgress(0.0)
        val FINISHED = WorkerProgress(-1.0)
    }
}

var MutableLiveData<WorkerProgress>.percentage: Double
    get() = this.value?.percentage ?: 0.0
    set(value) = postValue(WorkerProgress(value))

