package mce.server

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import mce.ast.pack.PackMetadata
import mce.ast.pack.PackMetadataSection
import mce.ast.pack.ResourceLocation
import mce.pass.backend.Generator
import mce.pass.backend.ResourceType
import mce.util.DATA_PACK_FORMAT
import java.io.Closeable
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@ExperimentalSerializationApi
@Suppress("NOTHING_TO_INLINE", "OVERRIDE_BY_INLINE")
class ZipGenerator(name: String) : Generator, Closeable {
    val output: ZipOutputStream = ZipOutputStream(File("$name.zip").outputStream().buffered())
    val arrays: MutableMap<String, ByteArray> = mutableMapOf()

    init {
        output.putNextEntry(ZipEntry("pack.mcmeta"))
        Json.encodeToStream(PackMetadata(PackMetadataSection("", DATA_PACK_FORMAT)), output)
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
