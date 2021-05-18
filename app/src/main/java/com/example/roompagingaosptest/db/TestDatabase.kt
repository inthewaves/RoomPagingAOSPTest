package com.example.roompagingaosptest

import android.content.Context
import androidx.annotation.GuardedBy
import androidx.room.*
import com.example.roompagingaosptest.db.AppInfo

const val CURRENT_DB_VERSION = 1

@Database(
    entities = [AppInfo::class],
    version = CURRENT_DB_VERSION,
    exportSchema = false
)
abstract class TestDatabase : RoomDatabase() {
    abstract fun appInfoDao(): AppInfoDao

    override fun close() {
        synchronized(TestDatabase::class) {
            super.close()
            instance = null
        }
    }

    companion object {
        private const val DATABASE_NAME = "roomdatabasetest.db"
        @GuardedBy("TestDatabase::class")
        @Volatile
        private var instance: TestDatabase? = null

        fun getInstance(context: Context): TestDatabase =
            synchronized(TestDatabase::class) {
                instance ?: buildDatabaseInstance(context).also { instance = it }
            }

        private fun buildDatabaseInstance(context: Context): TestDatabase =
            Room.databaseBuilder(context, TestDatabase::class.java, DATABASE_NAME)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
