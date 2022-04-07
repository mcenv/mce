package mce.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.createFile
import kotlin.io.path.writeLines

@OptIn(ExperimentalCli::class)
object Init : Subcommand("init", "Initialize project") {
    override fun execute() {
        Paths.get("pack.mce").createFile() // TODO: add content
        Paths.get("pom.xml").writeLines(
            listOf(
                "<dependencies>",
                "</dependencies>",
            )
        )
        Files.createDirectory(Paths.get("src"))
    }
}
