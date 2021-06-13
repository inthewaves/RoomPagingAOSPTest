package com.example.roompagingaosptest.job.jobchain

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Parcel

private const val EXTRA_NEXT_JOB_INFO = "next_job_info"

inline class JobChainInfo(private val params: JobParameters) {
    val nextJobInfo: JobInfo?
        get() {
            return params.transientExtras.getParcelable<JobInfo>(EXTRA_NEXT_JOB_INFO)


            val nextJobInfoParcel: Parcel? = params.extras.getIntArray(EXTRA_NEXT_JOB_INFO)
                ?.let { array ->
                    Parcel.obtain().apply { readIntArray(array) }
                }
                ?: params.transientExtras.getIntArray(EXTRA_NEXT_JOB_INFO)
                    ?.let { byteArray ->
                        Parcel.obtain().apply { readIntArray(byteArray) }
                    }
            nextJobInfoParcel?.setDataPosition(0)

            return if (nextJobInfoParcel != null) {
                JobInfo.CREATOR.createFromParcel(nextJobInfoParcel)
                    .also { nextJobInfoParcel.recycle() }
            } else {
                null
            }
        }
}

class JobChain(val jobs: List<JobInfo>) {
    init {
        require(jobs.isNotEmpty()) { "jobs is empty" }
    }

    fun enqueue(jobScheduler: JobScheduler): Int {
        return jobScheduler.schedule(jobs.first())
    }

    class Builder(ctx: Context) {
        val context: Context = ctx.applicationContext

        private val _jobs = mutableListOf<JobInfo>()
        val jobs: List<JobInfo> = _jobs

        private var isDoneBuilding = false

        fun build(): JobChain {
            if (isDoneBuilding) error("can't build twice")

            // Run a check first so that any failures don't result in partially written results.
            _jobs.forEachIndexed { index, jobInfo ->
                if (index != 0) {
                    require(!jobInfo.isPeriodic) { "intermediate jobs can't be periodic" }
                    require(!jobInfo.isPersisted) { "intermediate jobs can't be persisted" }
                }
            }

            for (index in _jobs.indices.reversed()) {
                val jobInfo = _jobs[index]
                val nextJobInfo: JobInfo? = if (index + 1 <= _jobs.lastIndex) {
                    _jobs[index + 1]
                } else {
                    null
                }

                if (nextJobInfo != null) {
                    jobInfo.transientExtras.putParcelable(EXTRA_NEXT_JOB_INFO, nextJobInfo)
                }
            }

            isDoneBuilding = true
            return JobChain(_jobs)
        }

        inline fun <T : ChainingJobService> addJob(
            jobId: Int,
            clazz: Class<T>,
            crossinline jobInfoAction: JobInfo.Builder.() -> Unit = {}
        ) {
            val jobInfo = JobInfo.Builder(jobId, ComponentName(context, clazz))
                .apply(jobInfoAction)
                .build()

            (jobs as MutableList).add(jobInfo)
        }
    }
}

inline fun buildJobChain(
    context: Context,
    crossinline init: JobChain.Builder.() -> Unit
): JobChain {
    val builder = JobChain.Builder(context)
    builder.init()
    return builder.build()
}
