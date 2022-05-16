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
import mce.protocol.Request
import mce.protocol.Response
import mce.serialization.Mce
import mce.server.build.Build
import mce.server.build.Key
import mce.server.pack.Packs
import mce.util.Store
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.createFile
import kotlin.system.exitProcess

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
                        try {
                            val response: Response = when (val request = receiveDeserialized<Request>()) {
                                is Request.Hover -> server.hover(request)
                                is Request.Completion -> server.completion(request)
                                is Request.Exit -> exit()
                            }
                            sendSerialized<Response>(response)
                        } catch (t: Throwable) {
                            t.printStackTrace()
                        }
                    }
                }
            }
        }.start(true)
    }

    private fun exit(): Nothing {
        // TODO: post-process
        exitProcess(0)
    }

    suspend fun init() {
        withContext(Dispatchers.IO) {
            Paths.get("pack.mce").createFile() // TODO: add content
            Files.createDirectory(Paths.get("src"))
        }
    }

    suspend fun hover(request: Request.Hover): Response.Hover {
        val result = build.fetch(Key.ElabResult(request.name))
        val type = printTerm(Store(result.normalizer).quoteTerm(result.types[request.target]!!))
        return Response.Hover(type, request.id)
    }

    suspend fun completion(request: Request.Completion): Response.Completion {
        val result = build.fetch(Key.ElabResult(request.name))
        return Response.Completion(
            result.completions[request.target]?.let { completions ->
                completions.map { (name, type) -> Response.Completion.Item(name, printTerm(Store(result.normalizer).quoteTerm(type))) }
            } ?: emptyList(),
            request.id
        )
    }

    suspend fun build() {
        withContext(Dispatchers.IO) {
            build.fetch(Key.GenResult)
        }
    }
}
