package com.penink.vpn

import android.app.Application
import com.penink.vpn.data.NodeRepository

class PeninkApp : Application() {
    val repository: NodeRepository by lazy { NodeRepository(this) }
}