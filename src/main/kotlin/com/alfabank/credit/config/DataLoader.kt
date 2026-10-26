package com.alfabank.credit.config

import com.alfabank.credit.repository.ApplicationRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DataLoader(
    private val applicationRepository: ApplicationRepository
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(DataLoader::class.java)

    @Transactional
    override fun run(vararg args: String?) {
        applicationRepository.backfillVersions()
        backfillApplicationOwners()
    }

    private fun backfillApplicationOwners() {
        val orphaned = applicationRepository.findAll().filter { it.ownerUsername == null }
        if (orphaned.isEmpty()) return
        orphaned.forEach {
            it.ownerUsername = "client"
            applicationRepository.save(it)
        }
        log.info("Backfilled owner for {} applications", orphaned.size)
    }
}
