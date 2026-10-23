package com.alfabank.credit.service

import com.alfabank.credit.domain.Application
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class ApplicationAccessService {

    fun currentUsername(): String {
        return SecurityContextHolder.getContext().authentication?.name ?: "system"
    }

    fun isManager(): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication ?: return false
        return authentication.authorities.any { it.authority == "ROLE_MANAGER" }
    }

    /**
     * Менеджер видит все заявки, клиент — только свои.
     * ownerUsername == null — legacy-записи до появления владельца (бэкфилл делает DataLoader).
     */
    fun checkAccess(application: Application) {
        if (isManager()) return
        val owner = application.ownerUsername ?: return
        if (owner != currentUsername()) {
            throw AccessDeniedException("Access to this application is denied")
        }
    }
}
