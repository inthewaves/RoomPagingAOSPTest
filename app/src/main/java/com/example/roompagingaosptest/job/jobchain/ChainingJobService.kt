package com.example.roompagingaosptest.job.jobchain

import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobWorkItem
import android.util.Log
import com.example.roompagingaosptest.job.CoroutineJobService
import kotlinx.coroutines.coroutineScope

abstract class ChainingJobService : CoroutineJobService() {
    companion object {
        private const val TAG = "ChainingJobService"
    }

    protected enum class JobResult { FAILURE, SUCCESS, RETRY }

    /**
     * Does the work for the job in a coroutine. This function should not call [jobFinished]
     * directly, as the class will handle calling it for you. This function should also not use
     * [JobWorkItem], as this class uses [jobFinished] to communicate finished work and is thus
     * incompatible with [JobWorkItem].
     *
     * Exceptions that are thrown and uncaught are treated will reschedule the job.
     *
     * @return The [JobResult].
     * - The next job in the chain will be scheduled if and only if [JobResult.SUCCESS] is returned.
     * - Returning [JobResult.RETRY] gives the job another chance to reschedule and complete the job
     *   successfully to advance the job chain.
     * - Returning [JobResult.FAILURE] fails the job and doesn't run any of the dependents.
     */
    protected abstract suspend fun runJob(params: JobParameters): JobResult

    final override suspend fun doWork(params: JobParameters) {
        val result: JobResult = try {
            coroutineScope {
                runJob(params)
            }.also { Log.d(TAG, "doWork (subclass ${this::class.java.simpleName}): success") }
        } catch (e: JobServiceCoroutineCancellationException) {
            // Don't bother calling jobFinished if the coroutine was cancelled.
            // JobServiceCoroutineCancellationException is only used when the CoroutineJobService
            // has onStopJob or onDestroy called. Whether to reschedule should be handled by
            // onStopJobInner, so we just rethrow it.
            Log.d(TAG, "doWork (subclass ${this::class.java.simpleName}): cancellation exception", e)
            throw e
        } catch (e: Throwable) {
            Log.d(TAG, "doWork (subclass ${this::class.java.simpleName}): exception means retry", e)
            JobResult.RETRY
        }

        if (result == JobResult.SUCCESS) {
            val jobChainInfo = JobChainInfo(params)
            val nextJobInfo = jobChainInfo.nextJobInfo
            Log.d(TAG, "doWork (subclass ${this::class.java.simpleName}): next job is $nextJobInfo")
            if (nextJobInfo != null) {
                applicationContext
                    .getSystemService(JobScheduler::class.java)
                    .schedule(nextJobInfo)
            }
        }

        jobFinished(params, /*wantsReschedule=*/result == JobResult.RETRY)
    }
}