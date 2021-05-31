package com.example.roompagingaosptest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.roompagingaosptest.paging.AppInfoAdapter
import com.example.roompagingaosptest.work.PackageInsertJob
import com.example.roompagingaosptest.work.PackageInsertJobInputData
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivityFragment : Fragment() {
    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_main_activity, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = AppInfoAdapter(viewModel)
        val recyclerView = view.findViewById<RecyclerView>(R.id.appInfoRecyclerView)

        lifecycleScope.launch {
            recyclerView.apply {
                addItemDecoration(DividerItemDecoration(view.context, DividerItemDecoration.VERTICAL))
                this.adapter = adapter
                layoutManager = LinearLayoutManager(view.context)
            }

            viewModel.appInfos.collectLatest {
                adapter.submitData(it)
            }
        }

        val newPackage = view.findViewById<TextView>(R.id.package_name_text)
        val newVersion = view.findViewById<TextView>(R.id.version_text)

        val addButton = view.findViewById<Button>(R.id.addButton)
        addButton.setOnClickListener {
            val input = PackageInsertJobInputData.create(
                newPackage.text.toString(),
                newVersion.text.toString().toIntOrNull()
            )
            val request = OneTimeWorkRequestBuilder<PackageInsertJob>()
                .setInputData(input.data)
                .addTag(PackageInsertJob.createTag(input))
                .build()

            WorkManager.getInstance(it.context)
                .enqueue(request)
        }
    }
}