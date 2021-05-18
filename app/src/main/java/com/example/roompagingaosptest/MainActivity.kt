package com.example.roompagingaosptest

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.roompagingaosptest.db.AppInfo
import com.example.roompagingaosptest.paging.AppInfoAdapter
import com.example.roompagingaosptest.paging.AppInfoViewHolder
import com.example.roompagingaosptest.work.PackageInsertJob
import com.example.roompagingaosptest.work.PackageInsertJobInputData
import com.example.roompagingaosptest.work.VersionUpdateJobInput
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.lang.Exception
import java.lang.NumberFormatException

class MainActivity : AppCompatActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val adapter = AppInfoAdapter(viewModel)
        val recyclerView = findViewById<RecyclerView>(R.id.appInfoRecyclerView)

        lifecycleScope.launch {
            recyclerView.apply {
                addItemDecoration(DividerItemDecoration(this@MainActivity, DividerItemDecoration.VERTICAL))
                this.adapter = adapter
                layoutManager = LinearLayoutManager(this@MainActivity)
            }

            viewModel.appInfos.collectLatest {
                adapter.submitData(it)
            }
        }

        val newPackage = findViewById<TextView>(R.id.package_name_text)
        val newVersion = findViewById<TextView>(R.id.version_text)

        val addButton = findViewById<Button>(R.id.addButton)
        addButton.setOnClickListener {
            val input = PackageInsertJobInputData.create(
                newPackage.text.toString(),
                newVersion.text.toString().toIntOrNull()
            )
            val request = OneTimeWorkRequestBuilder<PackageInsertJob>()
                .setInputData(input.data)
                .addTag(PackageInsertJob.createTag(input))
                .build()

            WorkManager.getInstance(this@MainActivity)
                .enqueue(request)
        }
    }
}