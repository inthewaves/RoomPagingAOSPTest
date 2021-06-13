package com.example.roompagingaosptest.job.jobchain

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.job.JobWorkItem
import android.util.Log
import com.example.roompagingaosptest.job.CoroutineJobService
import kotlinx.coroutines.coroutineScope

/**
 * A [JobService] that has the ability to schedule other [JobService]s after successful completion.
 * This sequence of jobs is usually referred to as a "job chain".
 *
 * Job chains are currently not created declaratively.
 */
abstract class ChainingJobService : CoroutineJobService() {
    companion object {
        private const val TAG = "ChainingJobService"
        /** The length of the job chain */
        private const val EXTRA_CHAIN_LENGTH = "chain_length"
        /** The max length of a job chain, for safety reasons */
        private const val MAX_JOB_CHAIN_LENGTH = 25
    }

    protected sealed class JobResult {
        /**
         * A [JobResult] indicating a successful job. The [nextJobCreator] is optional, and it is
         * used to create the next job in the chain using the supplied [JobParameters]. If the
         * [nextJobCreator] is null pr [nextJobCreator] returns null, no further jobs will be
         * scheduled.
         */
        class Success(val nextJobCreator: (() -> JobInfo?)?) : JobResult()
        /**
         * A [JobResult] indicating a failed job. Next jobs in the chain will not be run, and the
         * failed job will not be rescheduled.
         */
        object Failure : JobResult()
        /**
         * A [JobResult] indicating a failed job that needs a retry. The failed job will be
         * rescheduled, but jobs that are next in the chain will not be created until a [Success].
         */
        object Retry : JobResult()
    }

    /**
     * Does the work for the job in a coroutine. This function should not call [jobFinished]
     * directly, as the class will handle calling it for you. This function should also not use
     * [JobWorkItem], as this class uses [jobFinished] to communicate finished work and is thus
     * incompatible with [JobWorkItem].
     *
     * This suspend function should be cancellable, as the [CoroutineJobService] may need to cancel
     * coroutines when constraints fail to be met. Use cancellable functions such as
     * [kotlinx.coroutines.ensureActive] and [kotlinx.coroutines.yield] or read the
     * [kotlinx.coroutines.isActive] Boolean property in long-running computational loops.
     *
     * Exceptions that are thrown and uncaught are treated will be treated as if [JobResult.Failure]
     * is returned.
     *
     * @return The [JobResult]: [JobResult.Success], [JobResult.Retry], or [JobResult.Failure].
     * - The next job in the chain will be scheduled if and only if [JobResult.Success] is returned.
     *   A lambda that creates the next job ([JobResult.Success.nextJobCreator]) will be used to
     *   create the next job instance. If the nextJobCreator is null or nextJobCreator returns null,
     *   no further jobs will be scheduled.
     * - Returning [JobResult.Retry] gives the job another chance to reschedule and complete the job
     *   successfully to advance the job chain.
     * - Returning [JobResult.Failure] fails the job and doesn't run any of the dependents.
     */
    protected abstract suspend fun runJob(params: JobParameters): JobResult

    final override suspend fun doWork(params: JobParameters) {
        val chainLength = params.extras.getInt(EXTRA_CHAIN_LENGTH, 1)
        Log.d(TAG, "doWork (subclass ${this::class.java.simpleName}): starting with chainLength $chainLength")
        val result: JobResult = try {
            // Wrap in a coroutineScope to ensure we catch exceptions thrown.
            coroutineScope {
                runJob(params)
            }
        } catch (e: JobServiceCoroutineCancellationException) {
            // Don't bother calling jobFinished if the coroutine was cancelled.
            // JobServiceCoroutineCancellationException is only used when the CoroutineJobService
            // has onStopJob or onDestroy called. Whether to reschedule should be handled by
            // onStopJobInner, so we just rethrow it.
            Log.d(TAG, "doWork (subclass ${this::class.java.simpleName}): cancellation exception", e)
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "doWork (subclass ${this::class.java.simpleName}): exception", e)
            JobResult.Failure
        }
        Log.d(TAG, "doWork (subclass ${this::class.java.simpleName}): result: $result")

        if (result is JobResult.Success) {
            val nextJob: JobInfo? = result.nextJobCreator?.invoke()
            if (nextJob != null) {
                if (chainLength <= MAX_JOB_CHAIN_LENGTH) {
                    nextJob.extras.putInt(EXTRA_CHAIN_LENGTH, chainLength + 1)
                    val jobSchedulerResult = applicationContext
                        .getSystemService(JobScheduler::class.java)
                        .schedule(nextJob)
                    if (jobSchedulerResult == JobScheduler.RESULT_SUCCESS) {
                        Log.d(TAG, "(${this::class.java.simpleName}) scheduled next job in chain")
                    } else {
                        Log.w(TAG, "(${this::class.java.simpleName}) failed to schedule next " +
                                "job in chain (result: $jobSchedulerResult)")
                    }
                } else {
                    Log.w(TAG, "(${this::class.java.simpleName}) failed to schedule next " +
                            "job in chain " +
                            "(chain length of $chainLength exceeds max of $MAX_JOB_CHAIN_LENGTH)")
                }
            }
        }

        jobFinished(params, /*wantsReschedule=*/result is JobResult.Retry)
    }
}
