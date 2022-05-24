package mce.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand

@ExperimentalCli
object Version : Subcommand("version", "Display version") {
    override fun execute() {
        println("0.1.0")
    }
}
