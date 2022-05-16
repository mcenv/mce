package mce.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking
import mce.pass.Config
import mce.server.Server

@ExperimentalCli
object Init : Subcommand("init", "Initialize pack") {
    override fun execute(): Unit = runBlocking {
        Server(Config).init()
    }
}
