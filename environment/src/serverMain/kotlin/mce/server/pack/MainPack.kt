package mce.server.pack

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.*

object MainPack : Pack {
    override fun fetch(name: String): String? {
        val path = Paths.get("src", "$name.mce")
        return if (path.exists()) path.readText() else null
    }

    override fun list(): Set<String> {
        val src = Paths.get("src")
        return Files.walk(src)
            .filter { !it.isDirectory() && it.extension == "mce" }
            .map { src.relativize(it).nameWithoutExtension }.toList().toSet()
    }
}
