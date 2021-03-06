package mce.server.pack

object Packs {
    private val packs: MutableList<Pack> = mutableListOf(StdPack, MainPack)

    fun fetch(name: String): String? =
        packs.firstNotNullOfOrNull {
            it.fetch(name)
        }

    fun list(): Set<String> = packs.flatMap { it.list() }.toSet()
}
