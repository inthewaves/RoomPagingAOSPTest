package com.example.roompagingaosptest.paging

import android.util.Log
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.example.roompagingaosptest.db.AppInfo
import com.example.roompagingaosptest.MainActivityViewModel

class AppInfoAdapter(
    private val viewModel: MainActivityViewModel
) : PagingDataAdapter<AppInfo, AppInfoViewHolder>(diffCallback) {
    companion object {
        private const val TAG = "AppInfoAdapter"
        val diffCallback = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(
                oldItem: AppInfo,
                newItem: AppInfo
            ) = oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(
                oldItem: AppInfo,
                newItem: AppInfo
            ) = oldItem == newItem
        }
    }

    override fun onViewRecycled(holder: AppInfoViewHolder) {
        super.onViewRecycled(holder)
        Log.d(TAG, "onViewRecycled: holder=${holder.appInfo}")
        holder.stopObserving()
    }

    override fun onBindViewHolder(holder: AppInfoViewHolder, position: Int) {
        Log.d(TAG, "onBindViewHolder: position=$position")
        holder.bind(getItem(position))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = AppInfoViewHolder(parent, viewModel)
}