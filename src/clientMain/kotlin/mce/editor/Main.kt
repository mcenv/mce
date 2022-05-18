package mce.editor

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import mce.protocol.Response
import mce.serialization.Mce

@DelicateCoroutinesApi
@ExperimentalSerializationApi
fun main() {
    MainScope().launch {
        HttpClient(Js) {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Mce)
            }
        }.use { client ->
            client.webSocket(host = "localhost", port = 51130) {
                electron.onOpenFile { _, path -> println(path) }

                while (true) {
                    val response = receiveDeserialized<Response>()
                    println(response) // TODO
                }
            }
        }
    }
}
