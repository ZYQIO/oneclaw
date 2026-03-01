package com.oneclaw.shadow.di

import android.util.Log
import com.oneclaw.shadow.tool.builtin.LoadSkillTool
import com.oneclaw.shadow.tool.engine.PermissionChecker
import com.oneclaw.shadow.tool.engine.ToolExecutionEngine
import com.oneclaw.shadow.tool.engine.ToolRegistry
import com.oneclaw.shadow.tool.js.EnvironmentVariableStore
import com.oneclaw.shadow.tool.js.JsExecutionEngine
import com.oneclaw.shadow.tool.js.JsToolLoader
import com.oneclaw.shadow.tool.js.bridge.LibraryBridge
import com.oneclaw.shadow.tool.skill.SkillFileParser
import com.oneclaw.shadow.tool.skill.SkillRegistry
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val toolModule = module {

    // JS Execution Engine (OkHttpClient, LibraryBridge)
    single { JsExecutionEngine(get(), get()) }

    // Environment Variable Store
    single { EnvironmentVariableStore(androidContext()) }

    // Library Bridge for shared JS libraries
    single { LibraryBridge(androidContext()) }

    // JS Tool Loader
    single { JsToolLoader(androidContext(), get(), get()) }

    // RFC-014: Skill infrastructure
    single { SkillFileParser() }
    single { SkillRegistry(androidContext(), get()).apply { initialize() } }
    single { LoadSkillTool(get()) }

    single {
        ToolRegistry().apply {
            // Only Kotlin built-in: LoadSkillTool
            try {
                register(get<LoadSkillTool>())
            } catch (e: Exception) {
                Log.e("ToolModule", "Failed to register load_skill: ${e.message}")
            }

            // Built-in JS tools from assets (replaces Kotlin tool registration)
            val loader: JsToolLoader = get()
            try {
                val builtinResult = loader.loadBuiltinTools()
                loader.registerTools(this, builtinResult.loadedTools, allowOverride = false)
                if (builtinResult.loadedTools.isNotEmpty()) {
                    Log.i("ToolModule", "Loaded ${builtinResult.loadedTools.size} built-in JS tool(s)")
                }
                builtinResult.errors.forEach { error ->
                    Log.e("ToolModule", "Built-in JS tool error [${error.fileName}]: ${error.error}")
                }
            } catch (e: Exception) {
                Log.e("ToolModule", "Failed to load built-in JS tools: ${e.message}")
            }

            // User JS tools from file system (can override built-in)
            try {
                val userResult = loader.loadTools()
                val conflicts = loader.registerTools(this, userResult.loadedTools, allowOverride = true)

                val totalErrors = userResult.errors + conflicts
                if (userResult.loadedTools.isNotEmpty()) {
                    Log.i("ToolModule", "Loaded ${userResult.loadedTools.size} user JS tool(s)")
                }
                totalErrors.forEach { error ->
                    Log.w("ToolModule", "User JS tool load error [${error.fileName}]: ${error.error}")
                }
            } catch (e: Exception) {
                Log.e("ToolModule", "Failed to load user JS tools: ${e.message}")
            }
        }
    }

    single { PermissionChecker(androidContext()) }

    single { ToolExecutionEngine(get(), get()) }
}
