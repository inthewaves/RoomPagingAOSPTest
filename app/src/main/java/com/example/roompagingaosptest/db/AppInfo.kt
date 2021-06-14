package com.example.roompagingaosptest.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    indices = [
        Index("packageName")
    ]
)
data class AppInfo(
    @ColumnInfo @PrimaryKey
    val packageName: String,
    @ColumnInfo
    val label: String?,
    @ColumnInfo
    val versionCode: Long,
    @ColumnInfo
    val lastUpdated: Long,
    @ColumnInfo
    val updateJobId: UUID? = null
)