package mce.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking
import mce.pass.Config
import mce.server.Server

@ExperimentalCli
object Build : Subcommand("build", "Build pack") {
    override fun execute(): Unit = runBlocking {
        Server(Config).build()
    }
}
