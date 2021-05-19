package com.example.roompagingaosptest.db

import android.content.Context
import androidx.annotation.GuardedBy
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.roompagingaosptest.AppInfoDao
import java.util.UUID
import java.util.concurrent.Executors

const val CURRENT_DB_VERSION = 1

@Database(
    entities = [AppInfo::class],
    version = CURRENT_DB_VERSION,
    exportSchema = false
)
@TypeConverters(TestDatabase.Converter::class)
abstract class TestDatabase : RoomDatabase() {
    abstract fun appInfoDao(): AppInfoDao

    override fun close() {
        synchronized(TestDatabase::class) {
            super.close()
            instance = null
        }
    }

    class Converter {
        @TypeConverter
        fun uuidToString(uuid: UUID?): String? = uuid?.toString()
        @TypeConverter
        fun stringToUuid(string: String?): UUID? = string?.let { UUID.fromString(it) }
    }

    companion object {
        private const val DATABASE_NAME = "roomdatabasetest.db"
        @GuardedBy("TestDatabase::class")
        @Volatile
        private var instance: TestDatabase? = null
        private val executor = Executors.newFixedThreadPool(8)

        fun getInstance(context: Context): TestDatabase =
            synchronized(TestDatabase::class) {
                instance ?: buildDatabaseInstance(context).also { instance = it }
            }

        private fun buildDatabaseInstance(context: Context): TestDatabase =
            Room.databaseBuilder(context, TestDatabase::class.java, DATABASE_NAME)
                .setQueryExecutor(executor)
                .setTransactionExecutor(executor)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
