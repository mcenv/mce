package mce.server.pack

interface Pack {
    fun fetch(name: String): String?

    fun list(): Set<String>
}
