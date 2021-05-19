package com.example.roompagingaosptest.paging

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.example.roompagingaosptest.MainActivityViewModel
import com.example.roompagingaosptest.R
import com.example.roompagingaosptest.db.AppInfo
import com.example.roompagingaosptest.db.ProgressDatabase
import com.example.roompagingaosptest.work.AppVersionUpdateJob
import com.example.roompagingaosptest.work.WorkerProgress
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class AppInfoViewHolder(
    parent: ViewGroup,
    private val viewModel: MainActivityViewModel
) : RecyclerView.ViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.app_info_item, parent, false)
) {
    private val applicationContext = parent.context.applicationContext
    private val progressWatcher = AppVersionUpdateJob.UpdaterWatcher.getInstance(applicationContext)
    companion object {
        private const val TAG = "AppInfoViewHolder"
    }

    private val lifecycle = parent.context as LifecycleOwner

    var appInfo: AppInfo? = null
        private set

    private val appName: TextView = itemView.findViewById(R.id.appTitleTextView)
    private val versionCode: TextView = itemView.findViewById(R.id.versionCodeTextView)
    private val lastUpdate: TextView = itemView.findViewById(R.id.lastUpdatedTextView)
    private val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
    private val updateButton: Button = itemView.findViewById(R.id.updateButton)
    private val progressBar: CircularProgressIndicator = itemView.findViewById(R.id.progressBar)
    init {
        progressBar.isVisible = false
        progressBar.isIndeterminate = false
        progressBar.max = 100
    }
    private val workManager = WorkManager.getInstance(itemView.context)
    private var progressLiveData: LiveData<List<WorkInfo>>? = null

    private var newProgressLiveData: LiveData<WorkerProgress>? = null

    private val database = ProgressDatabase.getInstance(applicationContext)

    private fun relaunchUpdateObserverJob(packageName: String) {
        progressJob?.cancel()
        progressJob = lifecycle.lifecycleScope.launch {
            database.appUpdateProgressDao().getProgressForPackage(packageName)
                .conflate()
                .distinctUntilChanged()
                .collect { percentage ->
                    Log.d(TAG, "${appInfo?.packageName} collected $percentage")
                    if (percentage == null) {
                        progressBar.isVisible = false
                        return@collect
                    }

                    progressBar.isVisible = true
                    progressBar.progress = (100 * percentage).roundToInt()
                }
        }
    }

    private var progressJob: Job? = null

    private val newObserver = Observer<WorkerProgress> { progress ->
        return@Observer

        if (progress == WorkerProgress.FINISHED) {
            progressBar.isVisible = false
            return@Observer
        }
        progressBar.isVisible = true
        progressBar.progress = (100 * progress.percentage).roundToInt()
    }

    private val updateObserver = object : Observer<List<WorkInfo>> {
        private var job: Job? = null
        private var isNewAppInfo = true
        fun toggleNewAppData() {
            isNewAppInfo = true
        }

        override fun onChanged(workInfoList: List<WorkInfo>?) {
            return

            val workInfo = workInfoList?.find { !it.state.isFinished }
            if (workInfo == null) {
                Log.d(TAG, "${appInfo?.packageName}: FAILED TO FIND IN PROGRESS JOB")
                progressBar.isVisible = false
                return
            }

            val progress = AppVersionUpdateJob.Progress(workInfo.progress)
            Log.d(
                TAG,
                "${appInfo?.packageName} Observing: ${progress.percentage} (isNewAppInfo: $isNewAppInfo)\n" +
                        "    The list if $workInfoList"
            )

            val animated = !isNewAppInfo
            if (workInfo.state.isFinished) {
                if (progressBar.isVisible) {
                    progressBar.isVisible = false
                    progressBar.setProgressCompat(0, animated)
                    stopObserving()
                } else {
                    stopObserving()
                }
            } else {
                progressBar.isVisible = true
                progressBar.setProgressCompat(
                    (100 * progress.percentage).roundToInt(),
                    animated
                )
            }
            isNewAppInfo = false

        }
    }

    init {
        updateButton.setOnClickListener {
            appInfo?.let { it ->
                val tag = AppVersionUpdateJob.WORK_TAG
                val workRequest = OneTimeWorkRequestBuilder<AppVersionUpdateJob>()
                    .addTag(tag)
                    .setInputData(AppVersionUpdateJob.Input(it).inputData)
                    .build()

                workManager.enqueueUniqueWork(
                    AppVersionUpdateJob.createName(it),
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    workRequest
                )

                /*
                progressLiveData?.removeObserver(updateObserver)
                progressLiveData = workManager.getWorkInfosForUniqueWorkLiveData(AppVersionUpdateJob.createName(it))
                    .apply { observe(lifecycle, updateObserver) }

                 */

                newProgressLiveData?.removeObserver(newObserver)
                newProgressLiveData = progressWatcher.getOrCreateProgressForPackage(it.packageName)
                    .apply { observe(lifecycle, newObserver) }
                Log.d(TAG, "Progress is ${newProgressLiveData?.value}")

                lifecycle.lifecycleScope.launch {
                    val list = workManager
                        .getWorkInfosForUniqueWork(AppVersionUpdateJob.createName(it))
                        .await()
                    Log.d(TAG, "All work for this package $it: $list")
                }

                relaunchUpdateObserverJob(it.packageName)
            }
        }

        lifecycle.lifecycleScope.launch {
            val deleteAppInfoActor = actor<AppInfo>(capacity = Channel.CONFLATED) {
                for (appInfo in channel) {
                    viewModel.deleteAppInfo(appInfo.packageName)
                    workManager.cancelUniqueWork(AppVersionUpdateJob.createName(appInfo))
                }
            }
            deleteButton.setOnClickListener {
                appInfo?.let { deleteAppInfoActor.offer(it) }
            }
        }
    }

    @UiThread
    fun stopObserving() {
        Log.d(TAG, "stopObserving(): appInfo=$appInfo")
        appInfo?.let {
            progressWatcher.removePackageIfComplete(it.packageName)
        }


        // progressBar.isVisible = false
        /*
        progressBar.setProgressCompat(0, false)
        updateObserver.toggleNewAppData()
        progressLiveData?.removeObserver(updateObserver)
        progressLiveData = null
         */

        newProgressLiveData?.removeObservers(lifecycle)

        progressJob?.cancel()
    }

    @UiThread
    fun bind(appInfo: AppInfo?) {
        updateObserver.toggleNewAppData()

        /*
        progressLiveData?.removeObserver(updateObserver)
        if (appInfo == null) {
            Log.d(TAG, "FAILED TO FIND APP INFO")
        }
        progressLiveData = appInfo?.let {
            workManager.getWorkInfosForUniqueWorkLiveData(AppVersionUpdateJob.createName(it))
                .apply {
                    observe(lifecycle, updateObserver)
                }
        }
         */
        this.appInfo = appInfo
        newProgressLiveData?.removeObserver(newObserver)
        newProgressLiveData = appInfo
            ?.let { progressWatcher.getOrCreateProgressForPackage(it.packageName) }
            ?.apply {
                observe(lifecycle, newObserver)
                newObserver.onChanged(this.value)
            }
        appName.text = appInfo?.packageName ?: ""
        versionCode.text = appInfo?.versionCode?.toString() ?: ""
        lastUpdate.text = appInfo?.lastUpdated?.toString() ?: ""

        appInfo?.let { relaunchUpdateObserverJob(it.packageName) }
    }
}