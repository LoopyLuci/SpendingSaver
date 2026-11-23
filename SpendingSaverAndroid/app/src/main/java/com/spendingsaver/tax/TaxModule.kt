package com.spendingsaver.tax

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides the TaxRepository as a singleton.
 */
@Module
@InstallIn(SingletonComponent::class)
object TaxModule {
    @Provides
    @Singleton
    fun provideTaxRepository(@ApplicationContext context: Context): TaxRepository {
        return TaxRepository.createTaxRepository(context)
    }
}
