package com.penink.vpn.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nodes")
data class VpnNode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String,
    val port: Int = 443,
    val password: String,
    val isSelected: Boolean = false
) {
    val cleanAddress: String
        get() = address.substringAfter("://", address).substringBefore("/")

    val usesTls: Boolean
        get() = port == 443 || address.startsWith("wss://")
}