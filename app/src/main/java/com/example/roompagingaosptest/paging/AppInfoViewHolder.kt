package com.example.roompagingaosptest.paging

import android.app.Application
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.core.view.isVisible
import androidx.lifecycle.*
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.example.roompagingaosptest.db.AppInfo
import com.example.roompagingaosptest.MainActivityViewModel
import com.example.roompagingaosptest.R
import com.example.roompagingaosptest.work.AppVersionUpdateJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class AppInfoViewHolder(
    parent: ViewGroup,
    private val viewModel: MainActivityViewModel
) : RecyclerView.ViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.app_info_item, parent, false)
) {
    val lifecycle = parent.context as LifecycleOwner

    var appInfo: AppInfo? = null
        private set

    private val appName: TextView = itemView.findViewById(R.id.appTitleTextView)
    private val versionCode: TextView = itemView.findViewById(R.id.versionCodeTextView)
    private val lastUpdate: TextView = itemView.findViewById(R.id.lastUpdatedTextView)
    private val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
    private val updateButton: Button = itemView.findViewById(R.id.updateButton)
    private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
    init {
        progressBar.isVisible = false
        // progressBar.isIndeterminate = false
        progressBar.max = 100
    }
    private val workManager = WorkManager.getInstance(itemView.context)
    private var progressLiveData: LiveData<WorkInfo>? = null

    private val updateObserver = Observer<WorkInfo> { workInfo ->
        val progress = AppVersionUpdateJob.Progress(workInfo.progress)
        Toast.makeText(itemView.context, "Observing: ${progress.percentage}", Toast.LENGTH_SHORT).show()
        Log.d(AppInfoViewHolder::class.simpleName, "Observing: ${progress.percentage}")
        workInfo ?: return@Observer
        if (workInfo.state.isFinished) {
            progressBar.isVisible = false
            progressBar.progress = 0
        } else {
            progressBar.isVisible = true
            progressBar.setProgress(50, false)
        }
    }

    init {
        updateButton.setOnClickListener {
            appInfo?.let {
                val workRequest = OneTimeWorkRequestBuilder<AppVersionUpdateJob>()
                    .setInputData(AppVersionUpdateJob.Input(it).inputData)
                    .build()

                workManager.enqueue(workRequest)

                progressLiveData?.removeObserver(updateObserver)
                progressLiveData = workManager.getWorkInfoByIdLiveData(workRequest.id)
                    .apply { observe(lifecycle, updateObserver) }
            }
        }

        lifecycle.lifecycleScope.launch {
            val deleteAppInfoActor = actor<AppInfo>(capacity = Channel.CONFLATED) {
                for (appInfo in channel) {
                    viewModel.deleteAppInfo(appInfo.packageName)
                }
            }
            deleteButton.setOnClickListener {
                appInfo?.let { deleteAppInfoActor.offer(it) }
            }
        }
    }

    fun stopObserving() {
        progressLiveData?.removeObserver(updateObserver)
    }

    @UiThread
    fun bind(appInfo: AppInfo?) {
        progressLiveData?.removeObserver(updateObserver)
        this.appInfo = appInfo
        appName.text = appInfo?.packageName ?: ""
        versionCode.text = appInfo?.versionCode?.toString() ?: ""
        lastUpdate.text = appInfo?.lastUpdated?.toString() ?: ""
    }
}