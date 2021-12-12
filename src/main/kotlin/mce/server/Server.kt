package mce.server

import java.util.*
import kotlin.system.exitProcess

class Server {
    suspend fun build(): Unit = TODO()

    suspend fun hover(id: UUID): HoverItem = TODO()

    fun exit(): Nothing = exitProcess(0)
}
