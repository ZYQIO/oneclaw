package com.oneclaw.shadow.feature.schedule.alarm

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ExactAlarmEventBus {
    private val _permissionNeeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val permissionNeeded: SharedFlow<Unit> = _permissionNeeded.asSharedFlow()

    fun emitPermissionNeeded() {
        _permissionNeeded.tryEmit(Unit)
    }
}
