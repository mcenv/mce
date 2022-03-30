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
import mce.serialization.Mce

@OptIn(ExperimentalCli::class)
object Launch : Subcommand("launch", "Launch server") {
    private const val DEFAULT_PORT: Int = 51130
    private val port: Int by option(ArgType.Int, "port", "p", "Port").default(DEFAULT_PORT)

    override fun execute() {
        embeddedServer(Netty, port = port) {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Mce)
            }
            routing {
                webSocket {
                    val frames = incoming.iterator()
                    while (frames.hasNext()) {
                        // TODO
                    }
                }
            }
        }.start(true)
    }
}
