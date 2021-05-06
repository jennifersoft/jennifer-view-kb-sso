package com.aries.kb.login

import com.aries.extension.data.UserData
import com.aries.extension.handler.SSOLoginHandler
import com.aries.extension.util.LogUtil
import com.aries.extension.util.PropertyUtil
import com.aries.kb.util.OAMInsensitiveMap
import com.aries.kb.util.OAMUtil
import javax.servlet.http.HttpServletRequest

class KbLoginAdapter : SSOLoginHandler {
    override fun preHandle(request: HttpServletRequest): UserData? {
        val headerMap = OAMInsensitiveMap()
        OAMUtil.httpHeaderToMap(request, headerMap)

        val sso_user_id: String = headerMap.get(PropertyUtil.getValue("kb_login", "KB_HEADER_KEY", "KB_USER_ID"))
        val sso_user_password = PropertyUtil.getValue("kb_login", "KB_USER_PASSWORD", "")

        println("header key : " + PropertyUtil.getValue("kb_login", "KB_HEADER_KEY", "KB_USER_ID"))
        println("preHandle check: $sso_user_id ($sso_user_password)")

        if (sso_user_id == null || sso_user_id.trim().isEmpty()) {
            LogUtil.error("sso_user_id not found")
            return null
        }

        return UserData(sso_user_id, sso_user_password)
    }
}