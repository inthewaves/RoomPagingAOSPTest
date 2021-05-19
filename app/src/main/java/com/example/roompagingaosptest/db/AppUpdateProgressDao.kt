package com.example.roompagingaosptest.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AppUpdateProgressDao {
    @Query("SELECT percentage FROM AppUpdateProgress WHERE packageName = :pkg")
    abstract fun getProgressForPackage(pkg: String): Flow<Double?>

    suspend fun updateProgressForPackage(pkg: String, percentage: Double) {
        if (update(pkg, percentage) < 1) {
            insertProgressForPackage(AppUpdateProgress(pkg, percentage))
        }
    }

    @Query("UPDATE AppUpdateProgress SET percentage = :percentage WHERE packageName = :pkg")
    protected abstract suspend fun update(pkg: String, percentage: Double): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertProgressForPackage(progress: AppUpdateProgress)

    @Query("DELETE FROM AppUpdateProgress WHERE packageName = :pkg")
    abstract suspend fun deletePackage(pkg: String)
}