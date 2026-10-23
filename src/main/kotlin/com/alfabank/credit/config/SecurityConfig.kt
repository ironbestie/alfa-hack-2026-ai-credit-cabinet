package com.alfabank.credit.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun userDetailsService(passwordEncoder: PasswordEncoder): UserDetailsService {
        val encodedPassword = passwordEncoder.encode("password")

        val client = User.withUsername("client")
            .password(encodedPassword)
            .roles("CLIENT")
            .build()

        val manager = User.withUsername("manager")
            .password(encodedPassword)
            .roles("MANAGER")
            .build()

        return InMemoryUserDetailsManager(client, manager)
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/api/quiz/**", "/api/offers/**", "/api/products/**").permitAll()
                    .requestMatchers("/api/manager/**").hasRole("MANAGER")
                    .requestMatchers("/api/applications/**").hasAnyRole("CLIENT", "MANAGER")
                    .anyRequest().authenticated()
            }
            .httpBasic { }
        return http.build()
    }
}
