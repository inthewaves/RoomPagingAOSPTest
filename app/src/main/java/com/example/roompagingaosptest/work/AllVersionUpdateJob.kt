package com.example.roompagingaosptest.work

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import com.example.roompagingaosptest.db.TestDatabase
import kotlinx.coroutines.delay
import java.lang.Exception

class AllVersionUpdateJob(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val input = VersionUpdateJobInput(inputData)

        val database = TestDatabase.getInstance(applicationContext)
        PackageInsertJobProgress.create(1.0/3.0).progressData
        delay(3500L)

        database.withTransaction {
            val dao = database.appInfoDao()
            val appInfoToUpdate = dao.getAppInfo(input.pkg) ?: throw Exception("not in db")
            dao.update(
                appInfoToUpdate.copy(
                    versionCode = appInfoToUpdate.versionCode + 1,
                    lastUpdated = System.currentTimeMillis() / 1000L
                )
            )
        }

        setProgress(PackageInsertJobProgress.create(2.0/3.0).progressData)
        delay(3500L)
        PackageInsertJobProgress.create(1.0).progressData
        return Result.success()
    }

    companion object {
        fun createTag(packageName: String) = "version-update-$packageName"
    }
}

inline class PackageInsertJobProgress(val progressData: Data) {
    val percentage: Double
        get() = progressData.getDouble(PROGRESS_PERCENT_KEY, 0.0)

    companion object {
        const val PROGRESS_PERCENT_KEY = "percent"
        fun create(percentage: Double) =
            PackageInsertJobProgress(workDataOf(PROGRESS_PERCENT_KEY to percentage))
    }
}

inline class VersionUpdateJobInput(private val inputData: Data) {
    val pkg: String
        get() = inputData.getString(PACKAGE_KEY)!!
    val newVersion: Int
        get() = inputData.getInt(VERSION_KEY, -1)

    companion object {
        private const val PACKAGE_KEY = "package"
        private const val VERSION_KEY = "version"

    }
}