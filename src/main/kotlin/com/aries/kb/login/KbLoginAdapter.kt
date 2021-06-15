package com.aries.kb.login

import com.aries.extension.data.UserData
import com.aries.extension.handler.SSOLoginHandler
import com.aries.extension.util.LogUtil
import com.aries.extension.util.PropertyUtil
import com.aries.kb.util.OAMInsensitiveMap
import com.aries.kb.util.OAMUtil
import com.aries.kb.util.SelfExpiringHashMap
import javax.servlet.http.HttpServletRequest

class KbLoginAdapter : SSOLoginHandler {
    companion object {
        const val ADAPTER_ID = "kb_login"
        val AUTH_KEYS = SelfExpiringHashMap<String, String>()
    }

    override fun preHandle(request: HttpServletRequest): UserData? {
        val isDebug = PropertyUtil.getValue(ADAPTER_ID, "KB_DEBUG_LOG", "true") == "true"

        val headerMap = OAMInsensitiveMap()
        OAMUtil.httpHeaderToMap(request, headerMap)

        if (isDebug)
            LogUtil.info("HEADER KEYS \"${headerMap.keys}\"")

        val userId: String? = headerMap.get("KB_USER_ID")
        val deviceId: String? = headerMap.get("KB_DEVICE_ID")
        val authKey: String? = headerMap.get("KB_AUTH_KEY")

        if (userId == null || deviceId == null || authKey == null) {
            LogUtil.error("NOT_EXIST_HEADERS \"$userId:$deviceId (${request.remoteAddr})\"")
            return null
        }

        val cachedAuthKey = AUTH_KEYS[userId + deviceId]
        if (authKey != cachedAuthKey) {
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