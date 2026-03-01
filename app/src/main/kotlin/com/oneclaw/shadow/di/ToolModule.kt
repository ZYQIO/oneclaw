package com.oneclaw.shadow.di

import android.util.Log
import com.oneclaw.shadow.core.model.ToolSourceInfo
import com.oneclaw.shadow.core.model.ToolSourceType
import com.oneclaw.shadow.tool.browser.BrowserContentExtractor
import com.oneclaw.shadow.tool.browser.BrowserScreenshotCapture
import com.oneclaw.shadow.tool.browser.WebViewManager
import com.oneclaw.shadow.tool.builtin.BrowserTool
import com.oneclaw.shadow.tool.builtin.CreateAgentTool
import com.oneclaw.shadow.tool.builtin.CreateScheduledTaskTool
import com.oneclaw.shadow.tool.builtin.LoadSkillTool
import com.oneclaw.shadow.tool.builtin.WebfetchTool
import com.oneclaw.shadow.tool.builtin.config.AddModelTool
import com.oneclaw.shadow.tool.builtin.config.CreateProviderTool
import com.oneclaw.shadow.tool.builtin.config.DeleteAgentTool
import com.oneclaw.shadow.tool.builtin.config.DeleteModelTool
import com.oneclaw.shadow.tool.builtin.config.DeleteProviderTool
import com.oneclaw.shadow.tool.builtin.config.FetchModelsTool
import com.oneclaw.shadow.tool.builtin.config.GetConfigTool
import com.oneclaw.shadow.tool.builtin.config.ListAgentsTool
import com.oneclaw.shadow.tool.builtin.config.ListModelsTool
import com.oneclaw.shadow.tool.builtin.config.ListProvidersTool
import com.oneclaw.shadow.tool.builtin.config.ListToolStatesTool
import com.oneclaw.shadow.tool.builtin.config.ManageEnvVarTool
import com.oneclaw.shadow.tool.builtin.config.SetConfigTool
import com.oneclaw.shadow.tool.builtin.config.SetDefaultModelTool
import com.oneclaw.shadow.tool.builtin.config.SetToolEnabledTool
import com.oneclaw.shadow.tool.builtin.config.UpdateAgentTool
import com.oneclaw.shadow.tool.builtin.config.UpdateProviderTool
import com.oneclaw.shadow.tool.engine.PermissionChecker
import com.oneclaw.shadow.tool.engine.ToolEnabledStateStore
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

    // RFC-019: schedule_task built-in tool
    single { CreateScheduledTaskTool(get()) }

    // RFC-020: create_agent built-in tool
    single { CreateAgentTool(get()) }

    // RFC-021: webfetch built-in tool (replaces JS webfetch)
    single { WebfetchTool(get()) }

    // RFC-022: Browser tool components
    single { BrowserScreenshotCapture() }
    single { BrowserContentExtractor(androidContext()) }
    single { WebViewManager(androidContext(), get(), get()) }
    single { BrowserTool(androidContext(), get()) }

    // RFC-017: Tool enabled state store
    single { ToolEnabledStateStore(androidContext()) }

    // RFC-036: Configuration management tools
    // Provider tools
    single { ListProvidersTool(get(), get()) }
    single { CreateProviderTool(get()) }
    single { UpdateProviderTool(get()) }
    single { DeleteProviderTool(get()) }

    // Model tools
    single { ListModelsTool(get()) }
    single { FetchModelsTool(get(), get()) }
    single { SetDefaultModelTool(get()) }
    single { AddModelTool(get()) }
    single { DeleteModelTool(get()) }

    // Agent tools
    single { ListAgentsTool(get()) }
    single { UpdateAgentTool(get()) }
    single { DeleteAgentTool(get()) }

    // Settings tools
    single { GetConfigTool(get()) }
    single { SetConfigTool(get(), get()) }

    // Env var tool
    single { ManageEnvVarTool(get()) }

    single {
        ToolRegistry().apply {
            // Only Kotlin built-in: LoadSkillTool
            try {
                register(get<LoadSkillTool>(), ToolSourceInfo.BUILTIN)
            } catch (e: Exception) {
                Log.e("ToolModule", "Failed to register load_skill: ${e.message}")
            }

            try {
                register(get<CreateScheduledTaskTool>(), ToolSourceInfo.BUILTIN)
            } catch (e: Exception) {
                Log.e("ToolModule", "Failed to register schedule_task: ${e.message}")
            }

            try {
                register(get<CreateAgentTool>(), ToolSourceInfo.BUILTIN)
            } catch (e: Exception) {
                Log.e("ToolModule", "Failed to register create_agent: ${e.message}")
            }

            try {
                register(get<WebfetchTool>(), ToolSourceInfo.BUILTIN)
            } catch (e: Exception) {
                Log.e("ToolModule", "Failed to register webfetch: ${e.message}")
            }

            try {
                register(get<BrowserTool>(), ToolSourceInfo.BUILTIN)
            } catch (e: Exception) {
                Log.e("ToolModule", "Failed to register browser: ${e.message}")
            }

            // RFC-036: Configuration management tools
            val configTools = listOf(
                get<ListProvidersTool>(),
                get<CreateProviderTool>(),
                get<UpdateProviderTool>(),
                get<DeleteProviderTool>(),
                get<ListModelsTool>(),
                get<FetchModelsTool>(),
                get<SetDefaultModelTool>(),
                get<AddModelTool>(),
                get<DeleteModelTool>(),
                get<ListAgentsTool>(),
                get<UpdateAgentTool>(),
                get<DeleteAgentTool>(),
                get<GetConfigTool>(),
                get<SetConfigTool>(),
                get<ManageEnvVarTool>()
            )
            configTools.forEach { tool ->
                try {
                    register(tool, ToolSourceInfo.BUILTIN)
                } catch (e: Exception) {
                    Log.e("ToolModule", "Failed to register ${tool.definition.name}: ${e.message}")
                }
            }

            // Tool state tools: pass `this` (ToolRegistry) to avoid circular Koin dependency
            try {
                register(ListToolStatesTool(this, get()), ToolSourceInfo.BUILTIN)
            } catch (e: Exception) {
                Log.e("ToolModule", "Failed to register list_tool_states: ${e.message}")
            }
            try {
                register(SetToolEnabledTool(this, get()), ToolSourceInfo.BUILTIN)
            } catch (e: Exception) {
                Log.e("ToolModule", "Failed to register set_tool_enabled: ${e.message}")
            }

            // Built-in JS tools from assets (replaces Kotlin tool registration)
            val loader: JsToolLoader = get()
            try {
                val builtinResult = loader.loadBuiltinTools()
                // Register built-in JS tools with BUILTIN source type
                for (tool in builtinResult.loadedTools) {
                    if (hasTool(tool.definition.name)) {
                        Log.w("ToolModule", "Built-in JS tool '${tool.definition.name}' conflicts with existing tool")
                        continue
                    }
                    register(tool, ToolSourceInfo(type = ToolSourceType.BUILTIN))
                    Log.i("ToolModule", "Registered built-in JS tool: ${tool.definition.name}")
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

                for (tool in userResult.loadedTools) {
                    // Determine source info: single-file tools = JS_EXTENSION
                    // Group tools (from array manifests) have their filePath set and
                    // the baseName from the loader can be used as groupName.
                    // For now we detect groups by checking if the tool has a non-null jsFilePath
                    // and if the JSON was an array (group) or object (single).
                    // The JsToolLoader does not currently expose groupName per tool,
                    // so we register all user tools as JS_EXTENSION for now.
                    // RFC-018 will refine this when JsToolLoader exposes group metadata.
                    val sourceInfo = ToolSourceInfo(
                        type = ToolSourceType.JS_EXTENSION,
                        filePath = tool.jsFilePath.ifEmpty { null }
                    )

                    if (hasTool(tool.definition.name)) {
                        // Override: unregister old, register new
                        unregister(tool.definition.name)
                        register(tool, sourceInfo)
                        Log.i("ToolModule", "User JS tool '${tool.definition.name}' overrides built-in")
                    } else {
                        register(tool, sourceInfo)
                        Log.i("ToolModule", "Registered user JS tool: ${tool.definition.name}")
                    }
                }

                userResult.errors.forEach { error ->
                    Log.w("ToolModule", "User JS tool load error [${error.fileName}]: ${error.error}")
                }
            } catch (e: Exception) {
                Log.e("ToolModule", "Failed to load user JS tools: ${e.message}")
            }
        }
    }

    single { PermissionChecker(androidContext()) }

    single { ToolExecutionEngine(get(), get(), get()) }
}
