package com.example.hayequipoapp.di

import com.example.hayequipoapp.data.repository.FriendGroupRepositoryImpl
import com.example.hayequipoapp.data.repository.MatchInvitationRepositoryImpl
import com.example.hayequipoapp.data.repository.MatchRepositoryImpl
import com.example.hayequipoapp.data.repository.MatchStatRepositoryImpl
import com.example.hayequipoapp.data.repository.PlayerRepositoryImpl
import com.example.hayequipoapp.data.repository.PlayerReviewRepositoryImpl
import com.example.hayequipoapp.data.repository.PlayerStatRepositoryImpl
import com.example.hayequipoapp.data.repository.SportRepositoryImpl
import com.example.hayequipoapp.data.repository.VenueRepositoryImpl
import com.example.hayequipoapp.domain.repository.FriendGroupRepository
import com.example.hayequipoapp.domain.repository.MatchInvitationRepository
import com.example.hayequipoapp.domain.repository.MatchRepository
import com.example.hayequipoapp.domain.repository.MatchStatRepository
import com.example.hayequipoapp.domain.repository.PlayerRepository
import com.example.hayequipoapp.domain.repository.PlayerReviewRepository
import com.example.hayequipoapp.domain.repository.PlayerStatRepository
import com.example.hayequipoapp.domain.repository.SportRepository
import com.example.hayequipoapp.domain.repository.VenueRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindSportRepository(impl: SportRepositoryImpl): SportRepository

    @Binds @Singleton
    abstract fun bindVenueRepository(impl: VenueRepositoryImpl): VenueRepository

    @Binds @Singleton
    abstract fun bindPlayerRepository(impl: PlayerRepositoryImpl): PlayerRepository

    @Binds @Singleton
    abstract fun bindFriendGroupRepository(impl: FriendGroupRepositoryImpl): FriendGroupRepository

    @Binds @Singleton
    abstract fun bindMatchRepository(impl: MatchRepositoryImpl): MatchRepository

    @Binds @Singleton
    abstract fun bindMatchInvitationRepository(impl: MatchInvitationRepositoryImpl): MatchInvitationRepository

    @Binds @Singleton
    abstract fun bindMatchStatRepository(impl: MatchStatRepositoryImpl): MatchStatRepository

    @Binds @Singleton
    abstract fun bindPlayerStatRepository(impl: PlayerStatRepositoryImpl): PlayerStatRepository

    @Binds @Singleton
    abstract fun bindPlayerReviewRepository(impl: PlayerReviewRepositoryImpl): PlayerReviewRepository
}
