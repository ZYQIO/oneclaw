package com.oneclaw.shadow.di

import com.oneclaw.shadow.tool.engine.ToolRegistry
import org.koin.dsl.module

val toolModule = module {
    single { ToolRegistry() }
    // PermissionChecker and ToolExecutionEngine will be added in Phase 3
    // Built-in tools will be registered in Phase 3
}
