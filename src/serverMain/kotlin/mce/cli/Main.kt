package mce.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ExperimentalCli

@OptIn(ExperimentalCli::class)
fun main(args: Array<String>) {
    ArgParser("mce").run {
        subcommands(
            Version,
            Test,
        )

        parse(args)
    }
}