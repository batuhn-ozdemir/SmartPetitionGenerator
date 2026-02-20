package com.gpproject.smartpetitiongenerator.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// Rapor Referansı: Figure 3.1 - AppDatabase [cite: 245]
@Database(
    entities = [UserProfile::class, PetitionEntity::class, TemplateEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun petitionDao(): PetitionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_petition_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}