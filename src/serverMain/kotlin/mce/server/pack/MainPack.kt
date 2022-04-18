package mce.server.pack

import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readText

object MainPack : Pack {
    override fun fetch(name: String): String? {
        val path = Paths.get("src", "$name.mce")
        return if (path.exists()) path.readText() else null
    }
}
