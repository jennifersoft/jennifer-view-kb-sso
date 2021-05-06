package com.aries.kb.api

import com.aries.extension.starter.PluginController
import com.aries.extension.util.PropertyUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import com.aries.kb.util.SelfExpiringHashMap
import com.aries.kb.util.AES

@RestController
class KbApiController @Autowired constructor() : PluginController() {
    companion object {
        val AUTH_KEYS = SelfExpiringHashMap<String, String>()
    }

    @GetMapping(value = [ "/kbapi/authkey" ])
    fun createAuthKey(
        @RequestParam(required = true) user_id: String,
        @RequestParam(required = true) device_id: String
    ): ResponseEntity<String> {
        val key = user_id + device_id
        val output = AES.encrypt(key,
            PropertyUtil.getValue("kb_plugin", "KB_PASSWORD_SALT", "jennifer5"))

        AUTH_KEYS.put(key, output,
            PropertyUtil.getValue("kb_plugin", "KB_VALIDATE_TIMEOUT", "3000").toLong())

        return ResponseEntity(output, HttpStatus.OK)
    }
}