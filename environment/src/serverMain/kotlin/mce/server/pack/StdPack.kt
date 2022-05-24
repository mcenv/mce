package mce.server.pack

object StdPack : Pack {
    override fun fetch(name: String): String? =
        StdPack::class.java.getResourceAsStream("/std/src/$name.mce")?.use {
            it.readAllBytes().toString(Charsets.UTF_8)
        }

    override fun list(): Set<String> = emptySet()
}
