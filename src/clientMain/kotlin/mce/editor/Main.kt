package mce.editor

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import mce.protocol.Response
import mce.serialization.Mce

fun main() {
    MainScope().launch {
        val client = HttpClient(Js) {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Mce)
            }
        }
        client.webSocket(host = "localhost", port = 51130) {
            while (true) {
                val response = receiveDeserialized<Response>()
                // TODO
            }
        }
    }
}
