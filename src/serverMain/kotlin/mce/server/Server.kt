package mce.server

import mce.phase.Id
import mce.phase.frontend.printTerm
import mce.phase.quoteTerm
import mce.server.build.Build
import mce.server.build.Key
import mce.util.run

class Server {
    internal val build: Build = Build()

    suspend fun hover(name: String, id: Id): HoverItem {
        val result = build.fetch(Key.ElabResult(name))
        val type = printTerm(quoteTerm(result.types[id]!!).run(result.normalizer))
        return HoverItem(type)
    }

    suspend fun completion(name: String, id: Id): List<CompletionItem> {
        val result = build.fetch(Key.ElabResult(name))
        return result.completions[id]?.let { completions ->
            completions.map { (name, type) -> CompletionItem(name, printTerm(quoteTerm(type).run(result.normalizer))) }
        } ?: emptyList()
    }
}
