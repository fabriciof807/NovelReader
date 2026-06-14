package com.novelreader.di

import com.novelreader.data.parser.FreeWebNovelParser
import com.novelreader.data.parser.NovelParser
import com.novelreader.data.parser.ReadNovelFullParser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ParserModule {

    @Binds
    @IntoSet
    abstract fun bindFreeWebNovelParser(impl: FreeWebNovelParser): NovelParser

    @Binds
    @IntoSet
    abstract fun bindReadNovelFullParser(impl: ReadNovelFullParser): NovelParser
}
