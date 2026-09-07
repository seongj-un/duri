package com.duri.user.domain

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {

    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User?
}
