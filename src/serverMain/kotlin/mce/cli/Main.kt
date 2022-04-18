package mce.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ExperimentalCli

@ExperimentalCli
fun main(args: Array<String>) {
    ArgParser("mce").run {
        subcommands(
            Build,
            Init,
            Launch,
            Test,
            Version,
        )

        parse(args)
    }
}
