package com.circulesearch.app.di

import com.circulesearch.app.data.repository.EndpointProfileRepositoryImpl
import com.circulesearch.app.data.repository.PermissionStatusRepositoryImpl
import com.circulesearch.app.data.repository.VisualSearchRepositoryImpl
import com.circulesearch.app.domain.repository.EndpointProfileRepository
import com.circulesearch.app.domain.repository.PermissionStatusRepository
import com.circulesearch.app.domain.repository.VisualSearchRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindEndpointProfileRepository(impl: EndpointProfileRepositoryImpl): EndpointProfileRepository

    @Binds
    @Singleton
    abstract fun bindPermissionStatusRepository(impl: PermissionStatusRepositoryImpl): PermissionStatusRepository

    @Binds
    @Singleton
    abstract fun bindVisualSearchRepository(impl: VisualSearchRepositoryImpl): VisualSearchRepository
}
