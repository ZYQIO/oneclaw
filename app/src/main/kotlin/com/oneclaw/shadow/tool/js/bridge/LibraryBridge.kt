package com.oneclaw.shadow.tool.js.bridge

import android.content.Context
import android.util.Log
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.function
import java.io.File

/**
 * Located in: tool/js/bridge/LibraryBridge.kt
 *
 * Injects a lib() function into the QuickJS context that loads
 * shared JavaScript libraries from bundled assets or internal storage.
 *
 * Usage in JS: const TurndownService = lib('turndown');
 */
class LibraryBridge(private val context: Context) {

    companion object {
        private const val TAG = "LibraryBridge"
        private const val ASSETS_LIB_DIR = "js/lib"
        private const val INTERNAL_LIB_DIR = "js/lib"
    }

    // Cache evaluated library exports across tool executions within
    // the same app session. Libraries are pure and deterministic,
    // so caching is safe.
    // Key: library name, Value: JS source code
    private val sourceCache = mutableMapOf<String, String>()

    /**
     * Inject the lib() function into a QuickJS context.
     * Must be called before evaluating tool code.
     *
     * Because QuickJS contexts are fresh per execution, we cannot cache
     * evaluated JS objects across executions. Instead we cache the source
     * code and re-evaluate it per context. The evaluation cost for
     * Turndown (~20KB) is < 50ms.
     */
    fun inject(quickJs: QuickJs) {
        quickJs.function("__loadLibSource") { args: Array<Any?> ->
            val name = args.getOrNull(0)?.toString()
                ?: throw IllegalArgumentException("lib: name argument required")
            loadLibrarySource(name)
        }

        // The actual lib() wrapper evaluates the source and extracts exports
        // via the CommonJS module.exports / exports pattern.
    }

    /**
     * JS wrapper code evaluated in the QuickJS context to provide lib().
     * Must be evaluated after inject() and before tool code.
     */
    val LIB_WRAPPER_JS = """
        const __libCache = {};
        function lib(name) {
            if (__libCache[name]) return __libCache[name];
            const __source = __loadLibSource(name);
            // CommonJS-style module wrapper
            const module = { exports: {} };
            const exports = module.exports;
            const fn = new Function('module', 'exports', __source);
            fn(module, exports);
            const result = (Object.keys(module.exports).length > 0)
                ? module.exports
                : exports;
            __libCache[name] = result;
            return result;
        }
    """.trimIndent()

    private fun loadLibrarySource(name: String): String {
        // Check source cache first
        sourceCache[name]?.let { return it }

        // Sanitize: library name must be alphanumeric + hyphens + underscores
        if (!name.matches(Regex("^[a-zA-Z][a-zA-Z0-9_-]*$"))) {
            throw IllegalArgumentException("Invalid library name: '$name'")
        }

        // Try assets first
        val assetPath = "$ASSETS_LIB_DIR/$name.min.js"
        val assetFallbackPath = "$ASSETS_LIB_DIR/$name.js"

        val source = tryLoadFromAssets(assetPath)
            ?: tryLoadFromAssets(assetFallbackPath)
            ?: tryLoadFromInternal(name)
            ?: throw IllegalArgumentException(
                "Library '$name' not found. Searched: assets/$assetPath, assets/$assetFallbackPath, internal/$INTERNAL_LIB_DIR/"
            )

        sourceCache[name] = source
        return source
    }

    private fun tryLoadFromAssets(path: String): String? {
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    private fun tryLoadFromInternal(name: String): String? {
        val dir = File(context.filesDir, INTERNAL_LIB_DIR)
        // Try .min.js first, then .js
        val minFile = File(dir, "$name.min.js")
        if (minFile.exists()) return minFile.readText()
        val plainFile = File(dir, "$name.js")
        if (plainFile.exists()) return plainFile.readText()
        return null
    }
}
