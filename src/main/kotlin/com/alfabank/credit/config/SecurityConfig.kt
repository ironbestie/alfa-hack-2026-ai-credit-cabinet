package com.alfabank.credit.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun userDetailsService(): UserDetailsService {
        val client = User.withDefaultPasswordEncoder()
            .username("client")
            .password("password")
            .roles("CLIENT")
            .build()

        val manager = User.withDefaultPasswordEncoder()
            .username("manager")
            .password("password")
            .roles("MANAGER")
            .build()

        return InMemoryUserDetailsManager(client, manager)
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/api/quiz/**", "/api/offers/**").permitAll()
                    .requestMatchers("/api/manager/**").hasRole("MANAGER")
                    .requestMatchers("/api/applications/**").hasAnyRole("CLIENT", "MANAGER")
                    .anyRequest().authenticated()
            }
            .httpBasic { }
        return http.build()
    }
}