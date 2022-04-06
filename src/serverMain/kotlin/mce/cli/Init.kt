package mce.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import java.nio.file.Files
import java.nio.file.Paths

@OptIn(ExperimentalCli::class)
object Init : Subcommand("init", "Initialize project") {
    override fun execute() {
        Files.createFile(Paths.get("pack.mce")) // TODO: add content
        Files.createDirectory(Paths.get("src"))
    }
}
