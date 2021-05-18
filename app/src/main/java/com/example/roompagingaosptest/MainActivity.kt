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
import com.example.roompagingaosptest.db.AppInfo
import com.example.roompagingaosptest.paging.AppInfoAdapter
import com.example.roompagingaosptest.paging.AppInfoViewHolder
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
                Toast.makeText(this@MainActivity, "Received new paging data", Toast.LENGTH_LONG).show()
                adapter.submitData(it)
            }
        }

        val newPackage = findViewById<TextView>(R.id.package_name_text)
        val newVersion = findViewById<TextView>(R.id.version_text)

        lifecycleScope.launch {
            val addButton = findViewById<Button>(R.id.addButton)
            val addButtonActor = actor<AppInfo> {
                for (appInfo in channel) {
                    viewModel.insertAppInfo(appInfo)
                }
            }

            class CantMakeException(message: String) : Exception(message)

            addButton.setOnClickListener {
                try {
                    val pkg = newPackage.text.toString()
                    if (pkg.isBlank()) {
                        throw CantMakeException("invalid package name")
                    }

                    val version = newVersion.text.toString().toIntOrNull()
                    if (version == null || version < 1) {
                        throw CantMakeException("invalid version --- needs to be positive integer")
                    }

                    val appInfo = AppInfo(pkg, version, System.currentTimeMillis() / 1000)
                    if (!addButtonActor.offer(appInfo)) {
                        throw CantMakeException("offering to channel failed")
                    }
                } catch (e: CantMakeException) {
                    Snackbar.make(recyclerView, "Can't insert package: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }
}