package mce.server

import mce.ast.pack.ResourceLocation
import mce.pass.backend.Generator
import mce.pass.backend.ResourceType
import mce.util.DATA_PACK_FORMAT
import java.io.Closeable
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Suppress("NOTHING_TO_INLINE", "OVERRIDE_BY_INLINE")
class ZipGenerator(name: String) : Generator, Closeable {
    val output: ZipOutputStream = ZipOutputStream(File("$name.zip").outputStream().buffered())
    val arrays: MutableMap<String, ByteArray> = mutableMapOf()

    init {
        output.putNextEntry(ZipEntry("pack.mcmeta"))
        output.write("""{"pack":{"description":"","pack_format":$DATA_PACK_FORMAT}}""".toByteArray())
        output.closeEntry()
    }

    override inline fun entry(type: ResourceType, name: ResourceLocation, block: Generator.() -> Unit) {
        output.putNextEntry(ZipEntry("data/${name.namespace}/${type.type}/${name.path}.${type.extension}"))
        block()
        output.closeEntry()
    }

    override inline fun write(char: Char) {
        output.write(char.code)
    }

    override inline fun write(string: String) {
        output.write(arrays.computeIfAbsent(string) { string.toByteArray() })
    }

    override inline fun close() {
        output.close()
    }
}
