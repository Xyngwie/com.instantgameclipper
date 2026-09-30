package com.instantgameclipper.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ServiceState {
    private val running = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = running.asStateFlow()

    fun setRunning(value: Boolean) {
        running.value = value
    }
}
