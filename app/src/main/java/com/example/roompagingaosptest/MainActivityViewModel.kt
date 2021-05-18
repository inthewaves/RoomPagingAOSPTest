package com.example.roompagingaosptest

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.roompagingaosptest.db.AppInfo
import kotlinx.coroutines.flow.Flow

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
    private val database = TestDatabase.getInstance(application)

    val appInfos: Flow<PagingData<AppInfo>> =
        Pager(PagingConfig(pageSize = 60, enablePlaceholders = true, maxSize = 200)) {
            database.appInfoDao().allAppInfo()
        }.flow
        .cachedIn(viewModelScope)

    suspend fun incrementVersionCode(appInfo: AppInfo) {
        // database.appInfoDao().updateVersionCode(appInfo.packageName, appInfo.versionCode + 1L)
    }

    suspend fun deleteAppInfo(packageName: String) {
        database.appInfoDao().delete(packageName)
    }

    suspend fun insertAppInfo(appInfo: AppInfo) {
        database.appInfoDao().updateOrInsert(appInfo)
    }
}