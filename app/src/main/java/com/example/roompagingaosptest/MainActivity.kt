package com.example.roompagingaosptest

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.example.roompagingaosptest.db.AppInfo
import com.example.roompagingaosptest.db.TestDatabase
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    companion object {
        var activity: MainActivity? = null
    }

    override fun onDestroy() {
        super.onDestroy()
        activity = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (
            checkCallingOrSelfPermission("android.permission.INSTALL_PACKAGES") !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "android.permission.INSTALL_PACKAGES not granted", Toast.LENGTH_LONG).show()
            Log.e("MainActivity", "android.permission.INSTALL_PACKAGES not granted!")
        }
        activity = this
        setContentView(R.layout.activity_main)

        lifecycleScope.launch {
            val testDb = TestDatabase.getInstance(this@MainActivity)
            val appInfoDao = testDb.appInfoDao()

            if (appInfoDao.countAppInfo() <= 0) {
                Log.d("MainActivity", "inserting all apps")
                testDb.withTransaction {
                    packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
                        .forEach { packageInfo ->
                            appInfoDao.insert(
                                AppInfo(
                                    packageInfo.packageName,
                                    packageInfo.longVersionCode,
                                    packageInfo.lastUpdateTime
                                )
                            )
                        }
                }
            }
        }
    }
}