package com.oneclaw.remote.core.capture

import android.util.Base64
import com.oneclaw.remote.core.model.RemoteOperationResult
import com.oneclaw.remote.core.model.RemoteSnapshot
import com.oneclaw.remote.core.util.ShellCommandRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface RemoteCaptureBackend {
    fun isAvailable(): Boolean
    fun startStream(frameIntervalMs: Long = 1200L, onFrame: (RemoteSnapshot) -> Unit)
    fun stopStream()
    fun snapshot(): RemoteOperationResult<RemoteSnapshot>
}

class PrivilegedCaptureEngine(
    private val shellCommandRunner: ShellCommandRunner = ShellCommandRunner(),
    private val scope: CoroutineScope = CoroutineScope(Job() + Dispatchers.IO)
) : RemoteCaptureBackend {

    private var streamJob: Job? = null

    override fun isAvailable(): Boolean = shellCommandRunner.run("id", privileged = true).exitCode == 0

    override fun startStream(frameIntervalMs: Long, onFrame: (RemoteSnapshot) -> Unit) {
        stopStream()
        streamJob = scope.launch {
            while (isActive) {
                val nextFrame = snapshot()
                if (nextFrame.isSuccess) {
                    nextFrame.value?.let(onFrame)
                }
                delay(frameIntervalMs)
            }
        }
    }

    override fun stopStream() {
        streamJob?.cancel()
        streamJob = null
    }

    override fun snapshot(): RemoteOperationResult<RemoteSnapshot> {
        val result = shellCommandRunner.runBinary("screencap -p", privileged = true)
        if (result.exitCode != 0 || result.stdout.isEmpty()) {
            return RemoteOperationResult.error(result.stderr.ifBlank { "Unable to capture screen." })
        }
        return RemoteOperationResult.success(
            RemoteSnapshot(
                base64Data = Base64.encodeToString(result.stdout, Base64.NO_WRAP),
                capturedAt = System.currentTimeMillis()
            )
        )
    }
}

class ProjectionCaptureEngine : RemoteCaptureBackend {
    override fun isAvailable(): Boolean = false

    override fun startStream(frameIntervalMs: Long, onFrame: (RemoteSnapshot) -> Unit) = Unit

    override fun stopStream() = Unit

    override fun snapshot(): RemoteOperationResult<RemoteSnapshot> =
        RemoteOperationResult.error("MediaProjection capture is not implemented yet.")
}
