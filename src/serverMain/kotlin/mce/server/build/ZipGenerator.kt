package mce.server.build

import mce.phase.backend.gen.Generator
import mce.phase.backend.pack.ResourceLocation
import java.io.Closeable
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Suppress("NOTHING_TO_INLINE", "OVERRIDE_BY_INLINE")
class ZipGenerator(name: String) : Generator, Closeable {
    val output: ZipOutputStream = ZipOutputStream(File("$name.zip").outputStream().buffered())
    val arrays: MutableMap<String, ByteArray> = mutableMapOf()

    override inline fun entry(name: ResourceLocation, block: Generator.() -> Unit) {
        output.putNextEntry(ZipEntry("data/${name.namespace}/functions/${name.path}.mcfunction"))
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
