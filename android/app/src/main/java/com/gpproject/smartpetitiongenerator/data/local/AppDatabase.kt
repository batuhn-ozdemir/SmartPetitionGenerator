package com.gpproject.smartpetitiongenerator.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserProfile::class,
        PetitionEntity::class,
        ReadyTemplateEntity::class
    ],
    version = 4,
    exportSchema = false
)

abstract class AppDatabase : RoomDatabase() {

    // Provides access to all database operations.
    abstract fun petitionDao(): PetitionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Returns a single shared database instance for the whole application.
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_petition_db"
                )
                    // Recreates the database if no migration is provided.
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}