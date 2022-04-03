package mce.server

import mce.phase.Config
import mce.phase.frontend.printTerm
import mce.phase.quoteTerm
import mce.protocol.CompletionRequest
import mce.protocol.CompletionResponse
import mce.protocol.HoverRequest
import mce.protocol.HoverResponse
import mce.server.build.Build
import mce.server.build.Key
import mce.util.run

class Server(config: Config) {
    internal val build: Build = Build(config)

    suspend fun hover(request: HoverRequest): HoverResponse {
        val result = build.fetch(Key.ElabResult(request.name))
        val type = printTerm(quoteTerm(result.types[request.target]!!).run(result.normalizer))
        return HoverResponse(type, request.id)
    }

    suspend fun completion(request: CompletionRequest): List<CompletionResponse> {
        val result = build.fetch(Key.ElabResult(request.name))
        return result.completions[request.target]?.let { completions ->
            completions.map { (name, type) -> CompletionResponse(name, printTerm(quoteTerm(type).run(result.normalizer)), request.id) }
        } ?: emptyList()
    }
}
