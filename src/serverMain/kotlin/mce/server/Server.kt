package mce.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mce.pass.Config
import mce.pass.frontend.printTerm
import mce.pass.quoteTerm
import mce.protocol.CompletionRequest
import mce.protocol.CompletionResponse
import mce.protocol.HoverRequest
import mce.protocol.HoverResponse
import mce.server.build.Build
import mce.server.build.Key
import mce.server.pack.Packs
import mce.util.Store

class Server(config: Config) {
    internal val build: Build = Build(config, Packs)

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
        val result = build.fetch(Key.GenResult("main"))
        withContext(Dispatchers.IO) {
            ZipGenerator("main").use { generator ->
                result.generate(generator)
            }
        }
    }
}
