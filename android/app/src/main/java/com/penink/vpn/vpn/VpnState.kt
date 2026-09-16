package com.penink.vpn.vpn

import kotlinx.coroutines.flow.MutableStateFlow

object VpnState {
    val isRunning = MutableStateFlow(false)
    val status = MutableStateFlow("尚未連線")
}