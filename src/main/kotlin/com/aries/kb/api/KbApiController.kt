package com.aries.kb.api

import com.aries.extension.starter.PluginController
import com.aries.extension.util.PropertyUtil
import com.aries.kb.login.KbLoginAdapter
import com.aries.kb.util.AES
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*


@RestController
class KbApiController @Autowired constructor() : PluginController() {
    @GetMapping(value = [ "/kbapi/authkey" ])
    fun createAuthKey(
        @RequestParam(required = true) user_id: String,
        @RequestParam(required = true) device_id: String
    ): ResponseEntity<String> {
        val key = user_id + device_id
//        val authKey = KbLoginAdapter.AUTH_KEYS[key]

        val dateFormat = "yyyyMMddHHmmss"
        val date = Date(System.currentTimeMillis())
        val simpleDateFormat = SimpleDateFormat(dateFormat)
        val simpleDate = simpleDateFormat.format(date)
        val cutDate = simpleDate.substring(0, simpleDate.length - 1)

        print("~!!!! simpleDate : $cutDate\n")

        val output = URLEncoder.encode(AES.encrypt(
            key,
            PropertyUtil.getValue("kb_plugin", "KB_PASSWORD_SALT", "jennifer5") + cutDate
        ), "UTF-8")

        KbLoginAdapter.AUTH_KEYS.put(key, output, 10000)

        return ResponseEntity(output, HttpStatus.OK)
    }
}