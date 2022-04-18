package mce.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.createFile

@ExperimentalCli
object Init : Subcommand("init", "Initialize pack") {
    override fun execute() {
        Paths.get("pack.mce").createFile() // TODO: add content
        Files.createDirectory(Paths.get("src"))
    }
}
