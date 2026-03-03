package com.oneclaw.shadow.di

import com.oneclaw.shadow.data.git.AppGitRepository
import com.oneclaw.shadow.feature.memory.ui.GitHistoryViewModel
import com.oneclaw.shadow.tool.builtin.GitBundleTool
import com.oneclaw.shadow.tool.builtin.GitDiffTool
import com.oneclaw.shadow.tool.builtin.GitLogTool
import com.oneclaw.shadow.tool.builtin.GitRestoreTool
import com.oneclaw.shadow.tool.builtin.GitShowTool
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val gitModule = module {
    single { AppGitRepository(androidContext()) }
    single { GitLogTool(get()) }
    single { GitShowTool(get()) }
    single { GitDiffTool(get()) }
    single { GitRestoreTool(get()) }
    single { GitBundleTool(get()) }
    viewModel { GitHistoryViewModel(get()) }
}
