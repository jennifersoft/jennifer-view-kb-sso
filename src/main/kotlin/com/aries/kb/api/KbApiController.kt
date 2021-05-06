package com.aries.kb.api

import com.aries.extension.starter.PluginController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class KbApiController @Autowired constructor() : PluginController() {
    companion object {
    }

    @GetMapping(value = [ "/kbapi/authkey" ])
    fun createAuthKey(): ResponseEntity<ResponseData> {
        return ResponseEntity(ResponseData(true), HttpStatus.OK)
    }

    data class ResponseData(
        var result: Boolean = false
    )
}