package mce.server

import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import mce.pass.Config
import mce.pass.frontend.printTerm
import mce.pass.quoteTerm
import mce.protocol.*
import mce.serialization.Mce
import mce.server.build.Build
import mce.server.build.Key
import mce.server.pack.Packs
import mce.util.Store
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.createFile

class Server(config: Config) {
    internal val build: Build = Build(config, Packs)

    suspend fun launch(port: Int) {
        val server = Server(Config)

        embeddedServer(Netty, port = port) {
            install(WebSockets) @ExperimentalSerializationApi {
                contentConverter = KotlinxWebsocketSerializationConverter(Mce)
            }
            routing {
                webSocket {
                    while (true) {
                        val response = when (val request = receiveDeserialized<Request>()) {
                            is HoverRequest -> server.hover(request)
                            is CompletionRequest -> TODO()
                        }
                        sendSerialized<Response>(response)
                    }
                }
            }
        }.start(true)
    }

    suspend fun init() {
        withContext(Dispatchers.IO) {
            Paths.get("pack.mce").createFile() // TODO: add content
            Files.createDirectory(Paths.get("src"))
        }
    }

    suspend fun hover(request: HoverRequest): HoverResponse {
        val result = build.fetch(Key.ElabResult(request.name))
        val type = printTerm(Store(result.normalizer).quoteTerm(result.types[request.target]!!))
        return HoverResponse(type, request.id)
    }

    suspend fun completion(request: CompletionRequest): List<CompletionResponse> {
        val result = build.fetch(Key.ElabResult(request.name))
        return result.completions[request.target]?.let { completions ->
            completions.map { (name, type) -> CompletionResponse(name, printTerm(Store(result.normalizer).quoteTerm(type)), request.id) }
        } ?: emptyList()
    }

    suspend fun build() {
        withContext(Dispatchers.IO) {
            build.fetch(Key.GenResult)
        }
    }
}
