package com.example.roompagingaosptest.job

import android.app.job.JobParameters
import android.app.job.JobService
import android.app.job.JobWorkItem
import android.util.Log
import androidx.annotation.CallSuper
import androidx.collection.SimpleArrayMap
import com.example.roompagingaosptest.util.set
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

abstract class CoroutineJobService : JobService() {
    /**
     * For each work in the queue from [JobParameters.dequeueWork], the [workHandler] is invoked.
     * Then, [JobParameters.completeWork] is called.
     *
     * If an exception is thrown, [JobParameters.completeWork] is not called. This is to prevent
     * cancellations from losing incomplete work.
     */
    protected suspend inline fun JobParameters.forEachWork(
        crossinline workHandler: suspend (JobWorkItem) -> Unit
    ) {
        var work: JobWorkItem? = dequeueWork()
        while (work != null) {
            workHandler(work)
            completeWork(work)
            work = dequeueWork()
        }
    }

    /**
     * The [CoroutineDispatcher] to use for running the jobs for this [CoroutineJobService]. There
     * may be multiple coroutines running on this dispatcher if multiple jobs were scheduled for
     * this particular service.
     */
    abstract val dispatcher: CoroutineDispatcher

    private val coroutineScope by lazy { CoroutineScope(dispatcher + SupervisorJob()) }

    private val activeJobs = SimpleArrayMap<JobParameters, Job>()

    /**
     * Does the work for the job in a coroutine. You should use [jobFinished] when finished if
     * applicable, otherwise use [JobParameters.forEachWork] to deal with [JobWorkItem]s.
     *
     * This suspend function should be cancellable, as the [CoroutineJobService] may need to cancel
     * coroutines when constraints fail to be met. Use cancellable functions such as
     * [kotlinx.coroutines.ensureActive] and [kotlinx.coroutines.yield] or read the
     * [kotlinx.coroutines.isActive] Boolean property in long-running computational loops.
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
        Log.d(
            "CoroutineJobService",
            "(${this::class.java.simpleName}): " +
                    "onStartJob(), with " +
                    "params jobId=${params.jobId}, " +
                    "param hashcode: ${params.hashCode()}"
        )

        // Lazily start the job so that we have time to add it to the activeJobs map.
        val job = coroutineScope
            .launch(start = CoroutineStart.LAZY) { doWork(params) }
            .apply {
                invokeOnCompletion {
                    synchronized(activeJobs) {
                        Log.d(
                            "CoroutineJobService",
                            "(${this@CoroutineJobService::class.java.simpleName}): removing job id ${params.jobId}"
                        )
                        activeJobs.remove(params)
                    }
                }
            }
        synchronized(activeJobs) {
            activeJobs[params] = job
        }
        job.start()

        return true
    }

    final override fun onStopJob(params: JobParameters): Boolean {
        synchronized(activeJobs) {
            activeJobs[params]?.cancel(JobServiceCoroutineCancellationException("onStopJob called"))
            activeJobs.remove(params)
        }
        return onStopJobInner(params)
    }

    @CallSuper
    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel(JobServiceCoroutineCancellationException("onDestroy called"))
        synchronized(activeJobs) {
            activeJobs.clear()
        }
    }

    protected class JobServiceCoroutineCancellationException(
        override val message: String
    ) : CancellationException()
}