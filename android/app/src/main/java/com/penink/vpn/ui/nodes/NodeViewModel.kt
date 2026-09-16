package com.penink.vpn.ui.nodes

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.penink.vpn.data.NodeRepository
import com.penink.vpn.data.VpnNode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NodeViewModel(private val app: Application) : AndroidViewModel(app) {

    private val repository = NodeRepository(app)

    val nodes: StateFlow<List<VpnNode>> =
        repository.nodes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selected: StateFlow<VpnNode?> =
        repository.selected.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addNode(name: String, address: String, port: Int, password: String) {
        viewModelScope.launch {
            repository.add(name.trim(), address.trim(), port, password)
            Toast.makeText(app, "節點已新增", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateNode(id: Long, name: String, address: String, port: Int, password: String) {
        viewModelScope.launch {
            repository.update(id, name.trim(), address.trim(), port, password)
        }
    }

    fun deleteNode(node: VpnNode) {
        viewModelScope.launch { repository.delete(node) }
    }

    fun selectNode(node: VpnNode) {
        viewModelScope.launch { repository.select(node) }
    }
}