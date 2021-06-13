package com.example.roompagingaosptest.job.jobchain

import android.app.job.JobParameters
import android.app.job.JobWorkItem
import com.example.roompagingaosptest.job.CoroutineJobService
import kotlinx.coroutines.coroutineScope

abstract class ChainingJobService : CoroutineJobService() {
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
            }
        } catch (e: Throwable) {
            JobResult.RETRY
        }
        jobFinished(params, /*wantsReschedule=*/result == JobResult.RETRY)
    }

}