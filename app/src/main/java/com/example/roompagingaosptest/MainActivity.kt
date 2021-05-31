package com.example.roompagingaosptest

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

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
    }
}