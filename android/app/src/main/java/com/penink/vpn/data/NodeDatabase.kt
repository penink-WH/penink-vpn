package com.penink.vpn.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [VpnNode::class], version = 1, exportSchema = false)
abstract class NodeDatabase : RoomDatabase() {

    abstract fun nodeDao(): NodeDao

    companion object {
        @Volatile
        private var instance: NodeDatabase? = null

        fun get(context: Context): NodeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NodeDatabase::class.java,
                    "penink-vpn.db"
                ).build().also { instance = it }
            }
    }
}