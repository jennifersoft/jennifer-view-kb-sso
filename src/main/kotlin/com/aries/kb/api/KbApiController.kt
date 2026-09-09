package com.aries.kb.api

import com.aries.extension.starter.PluginController
import com.aries.extension.util.LogUtil
import com.aries.kb.auth.AuthKeyGenerator
import com.aries.kb.auth.AuthDiagnostics
import com.aries.kb.login.KbLoginAdapter
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
    init {
        AuthDiagnostics.runtime("api", KbApiController::class.java, KbLoginAdapter.AUTH_KEYS)
    }

    @GetMapping(value = [ "/kbapi/authkey" ])
    fun createAuthKey(
        @RequestParam(required = true) user_id: String,
        @RequestParam(required = true) device_id: String
    ): ResponseEntity<String> {
        return try {
            val cacheKey = AuthKeyGenerator.identityKey(user_id, device_id)
            val issuanceId = UUID.randomUUID().toString()
            synchronized(KbLoginAdapter.AUTH_KEYS) {
                val cachedKey = KbLoginAdapter.AUTH_KEYS[cacheKey]
                val authKey = cachedKey ?: AuthKeyGenerator.generate(user_id, device_id)
                if (cachedKey == null) KbLoginAdapter.AUTH_KEYS.put(cacheKey, authKey, 10_000L)
                val remainingMillis = KbLoginAdapter.AUTH_KEYS.remainingMillis(cacheKey)
                val event = if (cachedKey == null) "AUTH_KEY_ISSUED" else "AUTH_KEY_REUSED"
                LogUtil.info("$event ${AuthDiagnostics.details(user_id, device_id, authKey, KbLoginAdapter.AUTH_KEYS)} request_id=$issuanceId remaining_ms=$remainingMillis")
                ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header("X-KB-SSO-Version", AuthDiagnostics.VERSION)
                    .header("X-KB-SSO-Build", AuthDiagnostics.BUILD)
                    .header("X-KB-SSO-Issuance-Id", issuanceId)
                    .header("X-KB-SSO-Remaining-Millis", remainingMillis.toString())
                    .body(authKey)
            }
        } catch (exception: IllegalArgumentException) {
            LogUtil.warn("AUTH_KEY_REJECTED reason=invalid_identity ${AuthDiagnostics.details(user_id, device_id, null, KbLoginAdapter.AUTH_KEYS)}")
            ResponseEntity(HttpStatus.BAD_REQUEST)
        } catch (exception: IllegalStateException) {
            LogUtil.error("AUTH_KEY_REJECTED reason=generation_failed ${AuthDiagnostics.details(user_id, device_id, null, KbLoginAdapter.AUTH_KEYS)}")
            ResponseEntity(HttpStatus.SERVICE_UNAVAILABLE)
        }
    }
}
