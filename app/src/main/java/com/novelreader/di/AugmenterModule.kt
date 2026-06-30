package com.novelreader.di

import com.novelreader.domain.usecase.webimport.FreewebnovelListAugmenter
import com.novelreader.domain.usecase.webimport.NovelListAugmenter
import com.novelreader.domain.usecase.webimport.ReadNovelFullListAugmenter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class AugmenterModule {
    @Binds @IntoSet
    abstract fun bindFreewebnovel(impl: FreewebnovelListAugmenter): NovelListAugmenter

    @Binds @IntoSet
    abstract fun bindReadNovelFull(impl: ReadNovelFullListAugmenter): NovelListAugmenter
}
