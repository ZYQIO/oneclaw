package com.oneclaw.shadow.testutil

import android.content.Context
import androidx.room.Room
import com.oneclaw.shadow.data.local.db.AppDatabase

object TestDatabaseHelper {

    fun createInMemoryDatabase(context: Context): AppDatabase {
        return Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }
}
