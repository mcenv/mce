package mce.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand

@OptIn(ExperimentalCli::class)
object Version : Subcommand("version", "Display version") {
    override fun execute() {
        println("0.1.0")
    }
}
