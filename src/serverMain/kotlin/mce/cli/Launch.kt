package mce.cli

import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import mce.phase.Config
import mce.protocol.CompletionRequest
import mce.protocol.HoverRequest
import mce.protocol.Request
import mce.protocol.Response
import mce.serialization.Mce
import mce.server.Server

@OptIn(ExperimentalCli::class)
object Launch : Subcommand("launch", "Launch server") {
    private const val DEFAULT_PORT: Int = 51130
    private val port: Int by option(ArgType.Int, "port", "p", "Port").default(DEFAULT_PORT)

    override fun execute() {
        val server = Server(Config)

        embeddedServer(Netty, port = port) {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Mce)
            }
            routing {
                webSocket {
                    while (true) {
                        val response = when (val request = receiveDeserialized<Request>()) {
                            is HoverRequest -> server.hover(request)
                            is CompletionRequest -> TODO()
                            else -> TODO()
                        }
                        sendSerialized<Response>(response)
                    }
                }
            }
        }.start(true)
    }
}
