package mce.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand

@ExperimentalCli
object Test : Subcommand("test", "Run tests") {
    override fun execute() {
        TODO()
    }
}
