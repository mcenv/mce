package mce.cli

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlinx.coroutines.runBlocking
import mce.pass.Config
import mce.server.Server

@ExperimentalCli
object Launch : Subcommand("launch", "Launch server") {
    private const val DEFAULT_PORT: Int = 51130
    private val port: Int by option(ArgType.Int, "port", "p", "Port").default(DEFAULT_PORT)

    override fun execute(): Unit = runBlocking {
        Server(Config).launch(port)
    }
}
