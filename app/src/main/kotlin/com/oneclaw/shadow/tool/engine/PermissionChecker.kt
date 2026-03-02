package com.oneclaw.shadow.tool.engine

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Handles Android runtime permission checking and requesting.
 *
 * The coroutine suspends when permissions need to be requested and resumes
 * when the user responds to the system dialog.
 *
 * Lifecycle: bind to Activity in onCreate, unbind in onDestroy.
 */
class PermissionChecker(private val context: Context) {

    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var pendingContinuation: CancellableContinuation<Boolean>? = null

    /** Called from MainActivity.onCreate to wire up the permission result callback. */
    fun bindToActivity(launcher: ActivityResultLauncher<Array<String>>) {
        this.permissionLauncher = launcher
    }

    /** Called from MainActivity.onDestroy to avoid leaking Activity references. */
    fun unbind() {
        permissionLauncher = null
        pendingContinuation?.cancel()
        pendingContinuation = null
    }

    /**
     * Returns the subset of [permissions] that have not been granted yet.
     */
    fun getMissingPermissions(permissions: List<String>): List<String> {
        if (permissions.isEmpty()) return emptyList()
        return permissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) !=
                PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Requests the given permissions. Suspends until the user responds.
     * Returns true if ALL permissions were granted.
     */
    suspend fun requestPermissions(permissions: List<String>): Boolean {
        val launcher = permissionLauncher ?: return false

        return suspendCancellableCoroutine { continuation ->
            pendingContinuation = continuation
            continuation.invokeOnCancellation { pendingContinuation = null }
            launcher.launch(permissions.toTypedArray())
        }
    }

    /**
     * Called by MainActivity when the system permission dialog result arrives.
     * Resumes the suspended coroutine.
     */
    fun onPermissionResult(permissions: Map<String, Boolean>) {
        val allGranted = permissions.values.all { it }
        pendingContinuation?.resume(allGranted)
        pendingContinuation = null
    }
}
