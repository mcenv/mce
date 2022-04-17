package mce.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.serializer

val byteArrayDescriptor = serializer<ByteArray>().descriptor
val intArrayDescriptor = serializer<IntArray>().descriptor
val longArrayDescriptor = serializer<LongArray>().descriptor

enum class NbtType {
    END,
    BYTE,
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    BYTE_ARRAY,
    STRING,
    LIST,
    COMPOUND,
    INT_ARRAY,
    LONG_ARRAY,
}

@ExperimentalSerializationApi
fun SerialKind.toNbtType(): NbtType =
    when (this) {
        PrimitiveKind.BOOLEAN -> NbtType.BYTE
        PrimitiveKind.BYTE -> NbtType.BYTE
        PrimitiveKind.SHORT -> NbtType.SHORT
        PrimitiveKind.CHAR -> NbtType.SHORT
        PrimitiveKind.INT -> NbtType.INT
        PrimitiveKind.LONG -> NbtType.LONG
        PrimitiveKind.FLOAT -> NbtType.FLOAT
        PrimitiveKind.DOUBLE -> NbtType.DOUBLE
        PrimitiveKind.STRING -> NbtType.STRING
        StructureKind.CLASS -> NbtType.COMPOUND
        StructureKind.LIST -> NbtType.LIST
        StructureKind.MAP -> NbtType.LIST
        StructureKind.OBJECT -> NbtType.BYTE
        else -> TODO()
    }
