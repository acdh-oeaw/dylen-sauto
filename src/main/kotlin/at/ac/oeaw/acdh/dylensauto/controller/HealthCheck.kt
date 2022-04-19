package at.ac.oeaw.acdh.dylensauto.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/")
class HealthCheck {
    @GetMapping("/")
    fun health(): String {
        return "Healthy"
    }

}