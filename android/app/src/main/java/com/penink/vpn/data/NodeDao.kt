package com.penink.vpn.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {

    @Query("SELECT * FROM nodes ORDER BY id DESC")
    fun getAll(): Flow<List<VpnNode>>

    @Query("SELECT * FROM nodes ORDER BY id DESC")
    suspend fun getAllOnce(): List<VpnNode>

    @Query("SELECT * FROM nodes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): VpnNode?

    @Query("SELECT * FROM nodes WHERE isSelected = 1 LIMIT 1")
    fun getSelected(): Flow<VpnNode?>

    @Query("SELECT * FROM nodes WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedOnce(): VpnNode?

    @Insert
    suspend fun insert(node: VpnNode): Long

    @Update
    suspend fun update(node: VpnNode)

    @Delete
    suspend fun delete(node: VpnNode)

    @Query("UPDATE nodes SET isSelected = 0")
    suspend fun clearSelection()

    @Query("UPDATE nodes SET isSelected = 1 WHERE id = :id")
    suspend fun select(id: Long)
}