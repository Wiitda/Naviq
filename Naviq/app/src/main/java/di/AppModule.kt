package com.example.nav.di

import com.example.nav.api.CoordinateService
import com.example.nav.repository.NavigationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val BASE_URL = "https://apis.openapi.sk.com/tmap/geo/"

    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideCoordinateService(retrofit: Retrofit): CoordinateService {
        return retrofit.create(CoordinateService::class.java)
    }

    @Provides
    @Singleton
    fun provideNavigationRepository(service: CoordinateService): NavigationRepository {
        return NavigationRepository(service)
    }
}
