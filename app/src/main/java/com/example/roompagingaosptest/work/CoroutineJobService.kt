package com.example.roompagingaosptest.work

import android.app.job.JobParameters
import android.app.job.JobService
import android.app.job.JobWorkItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException

abstract class CoroutineJobService : JobService() {
    private var controlJob: Job? = null

    /**
     * For each work in the queue from [JobParameters.dequeueWork], the [workHandler] is invoked.
     * Then, [JobParameters.completeWork] is called.
     *
     * If an exception is thrown, [JobParameters.completeWork] is not called. This is to prevent
     * cancellations from losing incomplete work.
     */
    protected inline fun JobParameters.forEachWork(workHandler: (JobWorkItem) -> Unit) {
        var work: JobWorkItem? = dequeueWork()
        while (work != null) {
            workHandler(work)
            completeWork(work)
            work = dequeueWork()
        }
    }

    /**
     * The [CoroutineDispatcher] to use for running the job.
     */
    abstract val dispatcher: CoroutineDispatcher

    /**
     * Does the work for the job in a coroutine. You should use [jobFinished] when finished if
     * applicable, otherwise use [JobParameters.forEachWork] to deal with [JobWorkItem]s.
     *
     * @see onStartJob
     */
    abstract suspend fun doWork(params: JobParameters)

    /**
     * @return true to indicate to the JobManager whether you'd like to reschedule this job based on
     * the retry criteria provided at job creation-time; or false to end the job entirely.
     * Regardless of the value returned, your job must stop executing.
     *
     * @see onStopJob
     */
    abstract fun onStopJobInner(params: JobParameters): Boolean

    final override fun onStartJob(params: JobParameters): Boolean {
        val job = controlJob ?: Job().also { controlJob = it }
        val coroutineScope = CoroutineScope(dispatcher + job)
        coroutineScope.launch { doWork(params) }
        return true
    }

    final override fun onStopJob(params: JobParameters): Boolean {
        controlJob?.cancel(CancellationException("onStopJob called"))
        controlJob = null
        return onStopJobInner(params)
    }
}