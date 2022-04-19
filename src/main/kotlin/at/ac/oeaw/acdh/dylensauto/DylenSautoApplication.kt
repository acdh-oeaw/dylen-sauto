package at.ac.oeaw.acdh.dylensauto

import at.ac.oeaw.acdh.dylensauto.dao.SessionRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.event.EventListener

@SpringBootApplication
class DylenSautoApplication constructor(
    val sessionRepository: SessionRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun startup() {
        logger.info("Startup Complete...")
    }

}

fun main(args: Array<String>) {
    runApplication<DylenSautoApplication>(*args)
}
