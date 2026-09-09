package com.aries.kb.api

import com.aries.extension.starter.PluginController
import com.aries.extension.util.LogUtil
import com.aries.kb.auth.AuthKeyService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class KbApiController @Autowired constructor() : PluginController() {
    companion object {
        private const val VERSION = "3.0.0"
    }

    @GetMapping(value = [ "/kbapi/authkey" ])
    fun createAuthKey(
        @RequestParam(required = true) user_id: String,
        @RequestParam(required = true) device_id: String
    ): ResponseEntity<String> {
        return try {
            val authKey = AuthKeyService.shared().issue(user_id, device_id)
            val issuanceId = UUID.randomUUID().toString()
            LogUtil.info("AUTH_KEY_ISSUED version=$VERSION issuance_id=$issuanceId")
            ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-KB-SSO-Version", VERSION)
                .header("X-KB-SSO-Issuance-Id", issuanceId)
                .body(authKey)
        } catch (exception: IllegalArgumentException) {
            ResponseEntity(HttpStatus.BAD_REQUEST)
        } catch (exception: IllegalStateException) {
            ResponseEntity(HttpStatus.SERVICE_UNAVAILABLE)
        }
    }
}
