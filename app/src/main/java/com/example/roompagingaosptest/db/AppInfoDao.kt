package com.example.roompagingaosptest

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.roompagingaosptest.db.AppInfo

@Dao
abstract class AppInfoDao {
    @Transaction
    open suspend fun upsert(appInfo: AppInfo) {
        if (update(appInfo) <= 0) {
            insert(appInfo)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(appInfo: AppInfo)

    /**
     * @return The number of rows updated.
     */
    @Update
    abstract suspend fun update(appInfo: AppInfo): Int

    @Update
    suspend fun updateVersionCode(packageName: String, newVersionCode: Long) {
        updateVersionCodeInternal(packageName, newVersionCode, System.currentTimeMillis() / 1000)
    }

    @Query("DELETE FROM AppInfo WHERE packageName = :packageName")
    abstract suspend fun delete(packageName: String)

    @Query("""UPDATE AppInfo 
        SET versionCode = :newVersionCode, lastUpdated = :lastUpdateTimestamp 
        WHERE packageName = :packageName""")
    protected abstract suspend fun updateVersionCodeInternal(
        packageName: String,
        newVersionCode: Long,
        lastUpdateTimestamp: Long
    )

    @Query("SELECT * FROM AppInfo WHERE packageName = :packageName")
    abstract suspend fun getAppInfo(packageName: String): AppInfo?

    @Query("SELECT COUNT(*) FROM AppInfo")
    abstract suspend fun countAppInfo(): Long

    @Query("SELECT * FROM AppInfo ORDER BY IFNULL(label, packageName) COLLATE NOCASE")
    abstract fun allAppInfo(): PagingSource<Int, AppInfo>

    @Query("SELECT * FROM AppInfo ORDER BY packageName")
    abstract fun allAppInfoList(): List<AppInfo>

    @Query("SELECT * FROM AppInfo ORDER BY packageName")
    abstract fun allAppInfoLivedata(): LiveData<List<AppInfo>>
}