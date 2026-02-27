package com.oneclaw.shadow.di

import com.oneclaw.shadow.feature.provider.ProviderDetailViewModel
import com.oneclaw.shadow.feature.provider.ProviderListViewModel
import com.oneclaw.shadow.feature.provider.SetupViewModel
import com.oneclaw.shadow.feature.provider.usecase.FetchModelsUseCase
import com.oneclaw.shadow.feature.provider.usecase.SetDefaultModelUseCase
import com.oneclaw.shadow.feature.provider.usecase.TestConnectionUseCase
import com.oneclaw.shadow.feature.session.SessionListViewModel
import com.oneclaw.shadow.feature.session.usecase.BatchDeleteSessionsUseCase
import com.oneclaw.shadow.feature.session.usecase.CleanupSoftDeletedUseCase
import com.oneclaw.shadow.feature.session.usecase.CreateSessionUseCase
import com.oneclaw.shadow.feature.session.usecase.DeleteSessionUseCase
import com.oneclaw.shadow.feature.session.usecase.GenerateTitleUseCase
import com.oneclaw.shadow.feature.session.usecase.RenameSessionUseCase
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val featureModule = module {
    // Phase 2: Provider feature use cases
    factory { TestConnectionUseCase(get()) }
    factory { FetchModelsUseCase(get()) }
    factory { SetDefaultModelUseCase(get()) }

    // Phase 2: Provider feature view models
    viewModelOf(::ProviderListViewModel)
    viewModelOf(::ProviderDetailViewModel)
    viewModelOf(::SetupViewModel)

    // Phase 4 (RFC-005): Session feature use cases
    factory { CreateSessionUseCase(get()) }
    factory { DeleteSessionUseCase(get()) }
    factory { BatchDeleteSessionsUseCase(get()) }
    factory { RenameSessionUseCase(get()) }
    factory { GenerateTitleUseCase(get(), get(), get(), get()) }
    factory { CleanupSoftDeletedUseCase(get()) }

    // Phase 4 (RFC-005): Session feature view model
    viewModelOf(::SessionListViewModel)
}
