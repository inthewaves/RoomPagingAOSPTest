package com.example.roompagingaosptest.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.*

@Entity(
    indices = [
        Index("packageName")
    ]
)
data class AppInfo(
    @ColumnInfo @PrimaryKey
    val packageName: String,
    @ColumnInfo
    val versionCode: Int,
    @ColumnInfo
    val lastUpdated: Long,
    @ColumnInfo
    val updateJobId: UUID? = null
)