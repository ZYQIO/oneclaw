package ai.openclaw.app

import android.content.Context
import kotlinx.serialization.json.JsonObject

fun embeddedRuntimePodStatusSnapshot(context: Context): JsonObject = inspectEmbeddedRuntimePod(context).toJson()
