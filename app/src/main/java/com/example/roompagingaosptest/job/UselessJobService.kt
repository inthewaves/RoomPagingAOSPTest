package com.example.roompagingaosptest.job

import android.app.job.JobParameters
import android.util.Log
import com.example.roompagingaosptest.job.jobchain.ChainingJobService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

class UselessJobService : ChainingJobService() {
    override suspend fun runJob(params: JobParameters): JobResult {
        Log.d("UselessJobService", "doing nothing for 3 seconds")
        delay(3000L)
        return JobResult.SUCCESS
    }

    override val dispatcher: CoroutineDispatcher
        get() = Dispatchers.IO

    override fun onStopJobInner(params: JobParameters): Boolean {
        return true
    }

}