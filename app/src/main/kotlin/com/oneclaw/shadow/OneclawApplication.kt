package com.oneclaw.shadow

import android.app.Application
import com.oneclaw.shadow.core.theme.ThemeManager
import com.oneclaw.shadow.di.appModule
import com.oneclaw.shadow.di.databaseModule
import com.oneclaw.shadow.di.featureModule
import com.oneclaw.shadow.di.networkModule
import com.oneclaw.shadow.di.repositoryModule
import com.oneclaw.shadow.di.toolModule
import com.oneclaw.shadow.feature.session.usecase.CleanupSoftDeletedUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.java.KoinJavaComponent.get

class OneclawApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@OneclawApplication)
            modules(
                appModule,
                databaseModule,
                networkModule,
                repositoryModule,
                toolModule,
                featureModule
            )
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            get<CleanupSoftDeletedUseCase>(CleanupSoftDeletedUseCase::class.java)()
        }

        // RFC-009: Initialize theme from persisted setting
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            get<ThemeManager>(ThemeManager::class.java).initialize()
        }
    }
}
