package com.example.roompagingaosptest.paging

import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.example.roompagingaosptest.MainActivityViewModel
import com.example.roompagingaosptest.R
import com.example.roompagingaosptest.db.AppInfo
import com.example.roompagingaosptest.db.ProgressDatabase
import com.example.roompagingaosptest.work.AppVersionUpdateJob
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class AppInfoViewHolder(
    parent: ViewGroup,
    private val viewModel: MainActivityViewModel
) : RecyclerView.ViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.app_info_item, parent, false)
) {
    companion object {
        private const val TAG = "AppInfoViewHolder"
        private val expandInterpolator = FastOutSlowInInterpolator()
    }

    private val lifecycleScope = (parent.context as LifecycleOwner).lifecycleScope

    var appInfo: AppInfo? = null
        private set

    private val appName: TextView = itemView.findViewById(R.id.appTitleTextView)
    private val versionCode: TextView = itemView.findViewById(R.id.versionCodeTextView)
    private val lastUpdate: TextView = itemView.findViewById(R.id.lastUpdatedTextView)

    private val expandIcon: ImageView = itemView.findViewById(R.id.expandIconImageView)
    private val expandLinearLayout: LinearLayout = itemView.findViewById(R.id.expandLinearLayout)

    private val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
    private val updateButton: Button = itemView.findViewById(R.id.updateButton)
    private val progressBar: CircularProgressIndicator = itemView.findViewById(R.id.progressBar)
    init {
        progressBar.isVisible = false
        progressBar.max = 100

        val releaseNotes: TextView = itemView.findViewById(R.id.releaseNotesTextView)
        releaseNotes.text = Html.fromHtml(
            """
                <p>This is a paragraphed used to do my stuff.</p>
                <ul>
                    <li>This is my thing.</li>
                    <li>This is my other thing.</li>
                </ul>
                <p style="color:red;">Oh this is red now.</p>
                
                <h3>Header</h3>
                <p>This is a header containing my things.
                <a href="http://example.com">This is a link that goes to example.com</a>.</p>
            """.trimIndent().trimEnd('\n'),
            0
        )
        releaseNotes.movementMethod = LinkMovementMethod.getInstance()
    }
    private val workManager = WorkManager.getInstance(itemView.context)
    private val database = ProgressDatabase.getInstance(parent.context)

    private fun relaunchUpdateObserverJob(packageName: String) {
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
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
                    progressBar.progress = (100 * percentage).toInt()
                }
        }
    }

    private var progressJob: Job? = null
    init {
        updateButton.setOnClickListener {
            appInfo?.let { appInfo ->
                val tag = AppVersionUpdateJob.WORK_TAG
                val workRequest = OneTimeWorkRequestBuilder<AppVersionUpdateJob>()
                    .addTag(tag)
                    .setInputData(AppVersionUpdateJob.Input(appInfo).inputData)
                    .build()

                workManager.enqueueUniqueWork(
                    AppVersionUpdateJob.createName(appInfo),
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    workRequest
                )


                lifecycleScope.launch {
                    val list = workManager
                        .getWorkInfosForUniqueWork(AppVersionUpdateJob.createName(appInfo))
                        .await()
                    Log.d(TAG, "All work for this package $appInfo: $list")
                }

                relaunchUpdateObserverJob(appInfo.packageName)
            }
        }

        lifecycleScope.launch {
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
        progressJob?.cancel()
    }

    private fun setExpandState(icon: View, newExpandState: Boolean, shouldAnimate: Boolean) {
        val isExpanded: Boolean = icon.tag as? Boolean ?: false
        icon.apply {
            // Don't do any animations if trying to request an expand state that we're already
            // in.
            if (newExpandState == isExpanded) {
                if (tag == null) tag = newExpandState
                return
            }

            val newRotation = if (isExpanded) 0f else 180f
            if (shouldAnimate) {
                animate()
                    .setDuration(200L)
                    .rotation(newRotation)
            } else {
                rotation = newRotation
            }
            tag = !isExpanded
        }
        expandLinearLayout.apply {
            if (!shouldAnimate) {
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                isVisible = newExpandState
                alpha = 1.0f
                return
            }

            measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val currentHeight = measuredHeight
            val animation: Animation = if (!isExpanded) {
                alpha = 0.0f
                layoutParams.height = 0
                isVisible = true

                object : Animation() {
                    override fun applyTransformation(
                        interpolatedTime: Float,
                        t: Transformation?
                    ) {
                        alpha = interpolatedTime
                        layoutParams.height = if (interpolatedTime == 1f) {
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        } else {
                            (currentHeight * interpolatedTime).toInt()
                        }
                        requestLayout()
                    }
                }
            } else {
                alpha = 1.0f
                object : Animation() {
                    override fun applyTransformation(
                        interpolatedTime: Float,
                        t: Transformation?
                    ) {
                        alpha = 1.0f - interpolatedTime
                        if (interpolatedTime == 1f) {
                            isVisible = false
                        } else {
                            layoutParams.height = currentHeight -
                                    (currentHeight * interpolatedTime).toInt()
                            requestLayout()
                        }
                    }
                }
            }.apply {
                duration = 250L
                interpolator = expandInterpolator
            }
            startAnimation(animation)
        }
    }

    @UiThread
    fun bind(appInfo: AppInfo?) {
        this.appInfo = appInfo
        appName.text = appInfo?.packageName ?: ""
        versionCode.text = appInfo?.versionCode?.toString() ?: ""
        lastUpdate.text = appInfo?.lastUpdated?.toString() ?: ""
        appInfo?.let { relaunchUpdateObserverJob(it.packageName) }

        // Collapse when rebinded so that other items don't magically appear as expanded when
        // scrolling.
        setExpandState(expandIcon, newExpandState = false, shouldAnimate = false)
        expandIcon.setOnClickListener { icon ->
            val isExpended = icon.tag as? Boolean ?: false
            setExpandState(icon, !isExpended, true)
        }
    }
}