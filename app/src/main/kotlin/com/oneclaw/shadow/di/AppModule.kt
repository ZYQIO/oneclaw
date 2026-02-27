package com.oneclaw.shadow.di

import com.oneclaw.shadow.data.remote.adapter.ModelApiAdapterFactory
import com.oneclaw.shadow.data.security.ApiKeyStorage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single { ApiKeyStorage(androidContext()) }
    single { ModelApiAdapterFactory(get()) }
}
