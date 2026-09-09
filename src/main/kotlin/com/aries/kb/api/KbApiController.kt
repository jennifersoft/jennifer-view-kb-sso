package com.aries.kb.api

import com.aries.extension.starter.PluginController
import com.aries.kb.auth.AuthKeyService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class KbApiController @Autowired constructor() : PluginController() {
    @GetMapping(value = [ "/kbapi/authkey" ])
    fun createAuthKey(
        @RequestParam(required = true) user_id: String,
        @RequestParam(required = true) device_id: String
    ): ResponseEntity<String> {
        return try {
            ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(AuthKeyService.shared().issue(user_id, device_id))
        } catch (exception: IllegalArgumentException) {
            ResponseEntity(HttpStatus.BAD_REQUEST)
        } catch (exception: IllegalStateException) {
            ResponseEntity(HttpStatus.SERVICE_UNAVAILABLE)
        }
    }
}
