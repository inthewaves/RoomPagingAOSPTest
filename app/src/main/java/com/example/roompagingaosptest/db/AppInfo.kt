package com.example.roompagingaosptest.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class AppInfo(
    @ColumnInfo @PrimaryKey
    val packageName: String,
    @ColumnInfo
    val versionCode: Int,
    @ColumnInfo
    val lastUpdated: Long
)