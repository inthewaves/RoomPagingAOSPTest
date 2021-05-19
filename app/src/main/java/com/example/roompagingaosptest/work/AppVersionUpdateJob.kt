package com.example.roompagingaosptest.work

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.collection.arrayMapOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.roompagingaosptest.db.TestDatabase
import com.example.roompagingaosptest.db.AppInfo
import kotlinx.coroutines.Dispatchers
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
        val database = TestDatabase.getInstance(applicationContext)
        val input = Input(inputData)
        val updaterWatcher = UpdaterWatcher.getInstance(applicationContext)
        val progress = updaterWatcher.getOrPutProgressForPackage(input.pkg) as MutableLiveData<WorkerProgress>
        var percentage: Double = 0.0
        setProgress(Progress(0.0).progressData)
        progress.postValue(WorkerProgress.ZERO)
        repeat(10 * 66) {
            delay(100L)
            percentage += 0.001
            setProgress(Progress(percentage).progressData)
            progress.percentage = percentage
        }
        setProgress(Progress(2/3.0).progressData)
        progress.percentage = 2/3.0
        delay(2500L)

        val success = database.withTransaction {
            val dao = database.appInfoDao()
            val appInfo = dao.getAppInfo(input.pkg) ?: return@withTransaction false
            dao.updateVersionCode(appInfo.packageName, appInfo.versionCode + 1L)
            setProgress(Progress(0.9).progressData)
            progress.percentage = 0.9
            delay(2500L)
            true
        }

        setProgress(Progress(1.0).progressData)
        progress.postValue(WorkerProgress.FINISHED)
        delay(2500L)

        updaterWatcher.removePackage(input.pkg)
        return if (success) Result.success() else Result.failure()
    }

    class UpdaterWatcher private constructor(context: Context) {
        private val mapMutex = Mutex()
        private val appInfoMap: MutableMap<String, LiveData<WorkerProgress>> = arrayMapOf()
        private val workManager = WorkManager.getInstance(context)

        fun removePackage(pkg: String) {
            synchronized(appInfoMap) { appInfoMap.remove(pkg) }
        }

        fun getProgressForPackageOrNull(pkg: String): LiveData<WorkerProgress>? =
            synchronized(appInfoMap) {
                appInfoMap[pkg]
            }

        fun getOrPutProgressForPackage(pkg: String): LiveData<WorkerProgress> =
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

