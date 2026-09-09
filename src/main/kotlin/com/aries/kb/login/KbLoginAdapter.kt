package com.aries.kb.login

import com.aries.extension.data.UserData
import com.aries.extension.handler.SSOLoginHandler
import com.aries.extension.util.LogUtil
import com.aries.extension.util.PropertyUtil
import com.aries.kb.auth.AuthDiagnostics
import com.aries.kb.auth.AuthKeyGenerator
import com.aries.kb.auth.AuthKeyCodec
import com.aries.kb.util.SelfExpiringHashMap
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.servlet.http.HttpServletRequest

class KbLoginAdapter : SSOLoginHandler {
    companion object {
        const val ADAPTER_ID = "kb_login"
        val AUTH_KEYS = SelfExpiringHashMap<String, String>()
    }

    init {
        AuthDiagnostics.runtime("login", KbLoginAdapter::class.java, AUTH_KEYS)
    }

    override fun preHandle(request: HttpServletRequest): UserData? {
        val userId: String? = request.getParameter("user_id")
        val deviceId: String? = request.getParameter("device_id")
        val authKey: String? = request.getParameter("auth_key")

        val details = AuthDiagnostics.details(userId, deviceId, authKey, AUTH_KEYS)
        LogUtil.info("AUTH_CHECK $details")
        if (userId == null || deviceId == null || authKey == null) {
            LogUtil.error("NOT_EXIST_HEADERS $details")
            return null
        }

        if (!AuthKeyGenerator.validIdentity(userId) || !AuthKeyGenerator.validIdentity(deviceId)) {
            LogUtil.error("INVALID_KEY reason=invalid_format $details")
            return null
        }

        val normalizedKey = try {
            AuthKeyCodec.normalize(authKey)
        } catch (exception: IllegalArgumentException) {
            LogUtil.error("INVALID_KEY reason=invalid_encoding_or_length $details")
            return null
        }
        if (normalizedKey != authKey) {
            LogUtil.info("AUTH_KEY_NORMALIZED $details normalized_tag=${AuthDiagnostics.tag(normalizedKey)}")
        }

        val cacheKey = AuthKeyGenerator.identityKey(userId, deviceId)
        synchronized(AUTH_KEYS) {
            val storedKey = AUTH_KEYS[cacheKey]
            if (storedKey == null) {
                LogUtil.error("NOT_EXIST_KEY reason=missing_or_expired $details")
                return null
            }
            val cachedAuthKey = try {
                AuthKeyCodec.normalize(storedKey)
            } catch (exception: IllegalArgumentException) {
                LogUtil.error("INVALID_KEY reason=invalid_stored_encoding $details")
                return null
            }
            if (!MessageDigest.isEqual(normalizedKey.toByteArray(StandardCharsets.UTF_8), cachedAuthKey.toByteArray(StandardCharsets.UTF_8))) {
                LogUtil.error("INVALID_KEY reason=mismatch $details expected_tag=${AuthDiagnostics.tag(cachedAuthKey)} remaining_ms=${AUTH_KEYS.remainingMillis(cacheKey)}")
                return null
            }
            // Preserve v2 lookup/compare semantics: validation does not consume or renew the key.
            LogUtil.info("LOGIN key_verified=true jennifer_auth=pending $details remaining_ms=${AUTH_KEYS.remainingMillis(cacheKey)}")
        }
        return UserData(
            userId,
            PropertyUtil.getValue(ADAPTER_ID, "KB_JENNIFER_PASSWORD", "guest")
        )
    }
}
