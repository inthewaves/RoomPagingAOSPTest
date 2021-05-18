package com.example.roompagingaosptest.work

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.roompagingaosptest.TestDatabase
import com.example.roompagingaosptest.db.AppInfo
import kotlinx.coroutines.delay

class AppVersionUpdateJob(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    companion object {
        fun createTag(appInfo: AppInfo) = "version-update-job-${appInfo.packageName}"
    }

    override suspend fun doWork(): Result {
        val database = TestDatabase.getInstance(applicationContext)
        val input = Input(inputData)
        setProgress(Progress(0.0).progressData)
        delay(2500L)
        setProgress(Progress(1/3.0).progressData)
        delay(2500L)


        val success = database.withTransaction {
            val dao = database.appInfoDao()
            val appInfo = dao.getAppInfo(input.pkg) ?: return@withTransaction false
            dao.updateVersionCode(appInfo.packageName, appInfo.versionCode + 1L)
            setProgress(Progress(2/3.0).progressData)
            delay(2500L)
            true
        }

        setProgress(Progress(1.0).progressData)
        delay(2500L)
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

    class Progress constructor(val progressData: Data) {
        constructor(percentage: Double): this(workDataOf(PROGRESS_PERCENT_KEY to percentage))

        val percentage: Double
            get() = progressData.getDouble(PROGRESS_PERCENT_KEY, 0.0)

        companion object {
            const val PROGRESS_PERCENT_KEY = "percent"
        }
    }
}

