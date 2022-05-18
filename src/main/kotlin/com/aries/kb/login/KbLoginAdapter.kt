package com.aries.kb.login

import com.aries.extension.data.UserData
import com.aries.extension.handler.SSOLoginHandler
import com.aries.extension.util.LogUtil
import com.aries.extension.util.PropertyUtil
import com.aries.kb.util.SelfExpiringHashMap
import javax.servlet.http.HttpServletRequest

class KbLoginAdapter : SSOLoginHandler {
    companion object {
        const val ADAPTER_ID = "kb_login"
        val AUTH_KEYS = SelfExpiringHashMap<String, String>()
    }

    override fun preHandle(request: HttpServletRequest): UserData? {
        val userId: String? = request.getParameter("user_id")
        val deviceId: String? = request.getParameter("device_id")
        val authKey: String? = request.getParameter("auth_key")

        if (userId == null || deviceId == null || authKey == null) {
            LogUtil.error("NOT_EXIST_HEADERS \"$userId:$deviceId (${request.remoteAddr})\"")
            return null
        }

        // query string으로 받으면 +가 공백으로 치환됨
        val escapedAuthKey = authKey.replace(" ", "+")
        val cachedAuthKey = AUTH_KEYS[userId + deviceId]
        if (escapedAuthKey != cachedAuthKey) {
            LogUtil.error("INVALID_KEY \"$userId:$deviceId (${request.remoteAddr})\"")
            return null
        }

        LogUtil.info("LOGIN \"$userId:$deviceId (${request.remoteAddr})\"")
        return UserData(
            PropertyUtil.getValue(ADAPTER_ID, "KB_JENNIFER_ID", "guest"),
            PropertyUtil.getValue(ADAPTER_ID, "KB_JENNIFER_PASSWORD", "guest")
        )
    }
}