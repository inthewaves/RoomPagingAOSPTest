package com.example.roompagingaosptest.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.roompagingaosptest.db.AppInfo
import com.example.roompagingaosptest.db.TestDatabase

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

        return Result.success()
    }

    private class CantMakeException(message: String) : Exception(message)

    companion object {
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