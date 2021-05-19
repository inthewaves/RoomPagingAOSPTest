package com.example.roompagingaosptest.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [
        Index(value = ["packageName", "percentage"])
    ]
)
data class AppUpdateProgress(
    @ColumnInfo @PrimaryKey
    val packageName: String,
    @ColumnInfo
    val percentage: Double
)