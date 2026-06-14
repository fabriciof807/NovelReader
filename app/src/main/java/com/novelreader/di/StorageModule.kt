package com.novelreader.di

import com.novelreader.data.storage.CoverStorage
import com.novelreader.data.storage.CoverStorageImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {

    @Binds
    @Singleton
    abstract fun bindCoverStorage(impl: CoverStorageImpl): CoverStorage
}
