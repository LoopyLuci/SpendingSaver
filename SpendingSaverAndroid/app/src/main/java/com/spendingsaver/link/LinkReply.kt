package com.spendingsaver.link

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** A decoded `{"ok":..}` reply from the native link. */
sealed class LinkReply {
    data class Ok(val result: JsonObject) : LinkReply()
    data class Error(val message: String) : LinkReply()

    companion object {
        fun parse(text: String): LinkReply = try {
            val obj = JsonParser.parseString(text).asJsonObject
            if (obj.get("ok")?.asBoolean == true) {
                Ok(obj.get("result")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject())
            } else {
                Error(obj.get("error")?.asString ?: "link error")
            }
        } catch (_: Exception) {
            Error("The link returned an unreadable reply")
        }
    }
}
