package com.example.roompagingaosptest.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AppUpdateProgressDao {
    @Query("SELECT percentage FROM AppUpdateProgress WHERE packageName = :pkg")
    abstract fun getProgressForPackage(pkg: String): Flow<Double?>

    @Query("UPDATE AppUpdateProgress SET percentage = :percentage WHERE packageName = :pkg")
    abstract suspend fun setProgressForPackage(pkg: String, percentage: Double)

    @Query("DELETE FROM AppUpdateProgress WHERE packageName = :pkg")
    abstract suspend fun deletePackage(pkg: String)
}