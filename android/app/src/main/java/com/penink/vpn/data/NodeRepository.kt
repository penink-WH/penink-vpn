package com.penink.vpn.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class NodeRepository(context: Context) {

    private val dao = NodeDatabase.get(context).nodeDao()

    val nodes: Flow<List<VpnNode>> = dao.getAll()
    val selected: Flow<VpnNode?> = dao.getSelected()

    suspend fun getById(id: Long): VpnNode? = dao.getById(id)

    suspend fun getSelected(): VpnNode? = dao.getSelectedOnce()

    suspend fun add(name: String, address: String, port: Int, password: String) {
        val id = dao.insert(
            VpnNode(name = name, address = address, port = port, password = password)
        )
        if (dao.getSelectedOnce() == null) dao.select(id)
    }

    suspend fun update(id: Long, name: String, address: String, port: Int, password: String) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(name = name, address = address, port = port, password = password)
        )
    }

    suspend fun delete(node: VpnNode) {
        dao.delete(node)
        val rest = dao.getAllOnce()
        if (dao.getSelectedOnce() == null && rest.isNotEmpty()) {
            dao.select(rest.first().id)
        }
    }

    suspend fun select(node: VpnNode) {
        dao.clearSelection()
        dao.select(node.id)
    }
}