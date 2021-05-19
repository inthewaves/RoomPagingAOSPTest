package com.example.roompagingaosptest

import androidx.annotation.VisibleForTesting
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
interface AppInfoDao {
    @Transaction
    suspend fun updateOrInsert(appInfo: AppInfo) {
        if (update(appInfo) <= 0) {
            insert(appInfo)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(appInfo: AppInfo)

    /**
     * @return The number of rows updated.
     */
    @Update
    suspend fun update(appInfo: AppInfo): Int

    @Update
    suspend fun updateVersionCode(packageName: String, newVersionCode: Long) {
        updateVersionCodeInternal(packageName, newVersionCode, System.currentTimeMillis() / 1000)
    }

    @Query("DELETE FROM AppInfo WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("""UPDATE AppInfo 
        SET versionCode = :newVersionCode, lastUpdated = :lastUpdateTimestamp 
        WHERE packageName = :packageName""")
    @VisibleForTesting
    suspend fun updateVersionCodeInternal(packageName: String, newVersionCode: Long, lastUpdateTimestamp: Long)

    @Query("SELECT * FROM AppInfo WHERE packageName = :packageName")
    suspend fun getAppInfo(packageName: String): AppInfo?

    @Query("SELECT * FROM AppInfo ORDER BY packageName")
    fun allAppInfo(): PagingSource<Int, AppInfo>

    @Query("SELECT * FROM AppInfo ORDER BY packageName")
    fun allAppInfoList(): List<AppInfo>

    @Query("SELECT * FROM AppInfo ORDER BY packageName")
    fun allAppInfoLivedata(): LiveData<List<AppInfo>>
}