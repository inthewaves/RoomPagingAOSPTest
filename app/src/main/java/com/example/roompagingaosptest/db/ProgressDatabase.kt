package com.example.roompagingaosptest.db

import android.content.Context
import androidx.annotation.GuardedBy
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.util.UUID
import java.util.concurrent.Executors

@Database(
    entities = [AppUpdateProgress::class],
    version = 1,
    exportSchema = false
)
abstract class ProgressDatabase : RoomDatabase() {
    abstract fun appUpdateProgressDao(): AppUpdateProgressDao
    companion object {
        private const val DATABASE_NAME = "progress.db"
        @GuardedBy("ProgressDatabase::class")
        @Volatile
        private var instance: ProgressDatabase? = null

        private val executor = Executors.newFixedThreadPool(2)

        fun getInstance(context: Context): ProgressDatabase =
            synchronized(ProgressDatabase::class) {
                instance ?: buildDatabaseInstance(context).also { instance = it }
            }

        private fun buildDatabaseInstance(context: Context): ProgressDatabase =
            Room.inMemoryDatabaseBuilder(context, ProgressDatabase::class.java)
                .setQueryExecutor(executor)
                .setTransactionExecutor(executor)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}