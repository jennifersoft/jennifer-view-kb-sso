package com.aries.kb.login

import com.aries.extension.data.UserData
import com.aries.extension.handler.SSOLoginHandler
import com.aries.extension.util.LogUtil
import com.aries.extension.util.PropertyUtil
import com.aries.kb.api.KbApiController
import com.aries.kb.util.OAMInsensitiveMap
import com.aries.kb.util.OAMUtil
import com.aries.kb.util.SelfExpiringHashMap
import javax.servlet.http.HttpServletRequest

class KbLoginAdapter : SSOLoginHandler {
    companion object {
        val AUTH_KEYS = SelfExpiringHashMap<String, String>()
    }

    override fun preHandle(request: HttpServletRequest): UserData? {
        val headerMap = OAMInsensitiveMap()
        OAMUtil.httpHeaderToMap(request, headerMap)

        val userId: String = headerMap.get("KB_USER_ID")
        val deviceId: String = headerMap.get("KB_DEVICE_ID")
        val authKey: String = headerMap.get("KB_AUTH_KEY")

        if (userId == null || deviceId == null || authKey == null) {
            LogUtil.error("HTTP request attribute value required for authentication does not exist")
            return null
        }

        val cachedAuthKey = AUTH_KEYS[userId + deviceId]

        println("HTTP Request Headers: $userId, $deviceId, $authKey")
        println("Cached Key: $cachedAuthKey")

        if (authKey != cachedAuthKey) {
            LogUtil.error("The authentication key is not valid")
            return null
        }

        return UserData(
            PropertyUtil.getValue("kb_login", "KB_JENNIFER_ID", "guest"),
            PropertyUtil.getValue("kb_login", "KB_JENNIFER_PASSWORD", "guest")
        )
    }
}