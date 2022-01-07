package mce.phase

import mce.graph.Surface.Item
import mce.graph.Surface.Subtyping
import mce.graph.Surface.Term
import java.util.*

class Parse private constructor(source: String) {
    private val node: Node

    init {
        val pattern = Regex("([ ]*)([^ ]+) ([^ ]+) ([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}) ([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?: (.*))?")
        val lines = source.lines().dropLast(1).iterator()
        val stack = mutableListOf<Node>()

        fun readNode(line: String): Node {
            val values = pattern.matchEntire(line)!!.groupValues
            val depth = values[1].length
            val label = values[2]
            val type = values[3]
            val id = UUID.fromString(values[4])
            val parent = UUID.fromString(values[5])
            val value = values.getOrNull(6)

            lines.forEach {
                stack.add(readNode(it))
            }

            linkedMapOf<String, Node>().let { children ->
                while (stack.isNotEmpty() && depth + 1 == stack.last().depth) {
                    val node = stack.removeLast()
                    if (id != node.parent) error()
                    children[node.label] = node
                }

                return Node(depth, label, type, id, parent, value, children)
            }
        }

        node = readNode(lines.next())
    }

    private fun Node.parseItem(): Item = when (type) {
        "definition" -> Item.Definition(id, label, this["imports"].parseList { parseString() }, this["type"].parseTerm(), this["body"].parseTerm())
        else -> error()
    }

    private fun Node.parseTerm(): Term = when (type) {
        "hole" -> Term.Hole(id)
        "meta" -> Term.Meta(this["index"].parseInt(), id)
        "variable" -> Term.Variable(this["name"].parseString(), this["level"].parseInt(), id)
        "definition" -> Term.Definition(this["name"].parseString(), id)
        "let" -> Term.Let(this["name"].parseString(), this["init"].parseTerm(), this["body"].parseTerm(), id)
        "boolean_of" -> Term.BooleanOf(parseBoolean(), id)
        "byte_of" -> Term.ByteOf(parseByte(), id)
        "short_of" -> Term.ShortOf(parseShort(), id)
        "int_of" -> Term.IntOf(parseInt(), id)
        "long_of" -> Term.LongOf(parseLong(), id)
        "float_of" -> Term.FloatOf(parseFloat(), id)
        "double_of" -> Term.DoubleOf(parseDouble(), id)
        "string_of" -> Term.StringOf(parseString(), id)
        "byte_array_of" -> Term.ByteArrayOf(this["elements"].parseList { parseTerm() }, id)
        "int_array_of" -> Term.IntArrayOf(this["elements"].parseList { parseTerm() }, id)
        "long_array_of" -> Term.LongArrayOf(this["elements"].parseList { parseTerm() }, id)
        "list_of" -> Term.ListOf(this["elements"].parseList { parseTerm() }, id)
        "compound_of" -> Term.CompoundOf(this["elements"].parseList { parseTerm() }, id)
        "function_of" -> Term.FunctionOf(this["parameters"].parseList { parseString() }, this["body"].parseTerm(), id)
        "apply" -> Term.Apply(this["function"].parseTerm(), this["arguments"].parseList { parseTerm() }, id)
        "code_of" -> Term.CodeOf(this["element"].parseTerm(), id)
        "splice" -> Term.Splice(this["element"].parseTerm(), id)
        "union" -> Term.Union(this["variants"].parseList { parseTerm() }, id)
        "intersection" -> Term.Intersection(this["variants"].parseList { parseTerm() }, id)
        "boolean" -> Term.Boolean(id)
        "byte" -> Term.Byte(id)
        "short" -> Term.Short(id)
        "int" -> Term.Int(id)
        "long" -> Term.Long(id)
        "float" -> Term.Float(id)
        "double" -> Term.Double(id)
        "string" -> Term.String(id)
        "byte_array" -> Term.ByteArray(id)
        "int_array" -> Term.IntArray(id)
        "long_array" -> Term.LongArray(id)
        "list" -> Term.List(this["element"].parseTerm(), id)
        "compound" -> Term.Compound(this["elements"].parseList { label to parseTerm() }, id)
        "function" -> Term.Function(this["parameters"].parseList { parseSubtyping() }, this["resultant"].parseTerm(), id)
        "code" -> Term.Code(this["element"].parseTerm(), id)
        "type" -> Term.Type(id)
        else -> error()
    }

    private fun Node.parseSubtyping(): Subtyping = Subtyping(this["name"].parseString(), this["lower"].parseTerm(), this["upper"].parseTerm(), this["type"].parseTerm())

    private fun <E> Node.parseList(parse: Node.() -> E): List<E> = children.values.map(parse)

    private fun Node.parseBoolean(): Boolean = value!!.toBooleanStrict()

    private fun Node.parseByte(): Byte = value!!.toByte()

    private fun Node.parseShort(): Short = value!!.toShort()

    private fun Node.parseInt(): Int = value!!.toInt()

    private fun Node.parseLong(): Long = value!!.toLong()

    private fun Node.parseFloat(): Float = value!!.toFloat()

    private fun Node.parseDouble(): Double = value!!.toDouble()

    private fun Node.parseString(): String = value!!

    private fun error(): Nothing = throw Exception("malformed source")

    private data class Node(
        val depth: Int,
        val label: String,
        val type: String,
        val id: UUID,
        val parent: UUID,
        val value: String?,
        val children: Map<String, Node>
    ) {
        operator fun get(label: String): Node = children[label]!!
    }

    companion object {
        operator fun invoke(source: String): Item = Parse(source).run { node.parseItem() }
    }
}
