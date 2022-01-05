package mce.phase

import mce.graph.Surface.Item
import mce.graph.Surface.Subtyping
import mce.graph.Surface.Term
import java.util.*

class Parse private constructor(source: String) {
    private val node: Node

    init {
        val pattern = Regex("([ ]*)([a-z_:]+) ([a-z_:]+) ([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}) ([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?: (.*))?")
        val lines = source.lineSequence().iterator()
        val stack = mutableListOf<Node>()

        fun readNode(line: String): Node {
            val values = pattern.matchEntire(line)!!.groupValues
            val depth = values[1].length
            val label = values[2]
            val type = values[3]
            val id = UUID.fromString(values[4])
            val parent = UUID.fromString(values[5])
            val value = values.getOrNull(6)
            val children = linkedMapOf<String, Node>()

            while (lines.hasNext()) {
                val next = lines.next()
                if (next.isEmpty()) {
                    break
                }
                stack.add(readNode(next))
            }

            while (stack.isNotEmpty() && depth + 1 == stack.last().depth) {
                val node = stack.removeLast()
                children[node.label] = node
            }

            return Node(depth, label, type, id, parent, value, children)
        }

        node = readNode(lines.next())
    }

    private fun Node.parseItem(): Item = when (type) {
        "item:definition" -> Item.Definition(label, this["imports"].parseList { parseString() }, this["type"].parseTerm(), this["body"].parseTerm())
        else -> throw Exception("malformed source")
    }

    private fun Node.parseTerm(): Term = when (type) {
        "term:hole" -> Term.Hole(id)
        "term:meta" -> Term.Meta(this["index"].parseInt(), id)
        "term:variable" -> Term.Variable(this["name"].parseString(), this["level"].parseInt(), id)
        "term:definition" -> Term.Definition(this["name"].parseString(), id)
        "term:let" -> Term.Let(this["name"].parseString(), this["init"].parseTerm(), this["body"].parseTerm(), id)
        "term:boolean_of" -> Term.BooleanOf(this["value"].parseBoolean(), id)
        "term:byte_of" -> Term.ByteOf(this["value"].parseByte(), id)
        "term:short_of" -> Term.ShortOf(this["value"].parseShort(), id)
        "term:int_of" -> Term.IntOf(this["value"].parseInt(), id)
        "term:long_of" -> Term.LongOf(this["value"].parseLong(), id)
        "term:float_of" -> Term.FloatOf(this["value"].parseFloat(), id)
        "term:double_of" -> Term.DoubleOf(this["value"].parseDouble(), id)
        "term:string_of" -> Term.StringOf(this["value"].parseString(), id)
        "term:byte_array_of" -> Term.ByteArrayOf(this["elements"].parseList { parseTerm() }, id)
        "term:int_array_of" -> Term.IntArrayOf(this["elements"].parseList { parseTerm() }, id)
        "term:long_array_of" -> Term.LongArrayOf(this["elements"].parseList { parseTerm() }, id)
        "term:list_of" -> Term.ListOf(this["elements"].parseList { parseTerm() }, id)
        "term:compound_of" -> Term.CompoundOf(this["elements"].parseList { parseTerm() }, id)
        "term:function_of" -> Term.FunctionOf(this["parameters"].parseList { parseString() }, this["body"].parseTerm(), id)
        "term:apply" -> Term.Apply(this["function"].parseTerm(), this["arguments"].parseList { parseTerm() }, id)
        "term:code_of" -> Term.CodeOf(this["element"].parseTerm(), id)
        "term:splice" -> Term.Splice(this["element"].parseTerm(), id)
        "term:union" -> Term.Union(this["variants"].parseList { parseTerm() }, id)
        "term:intersection" -> Term.Intersection(this["variants"].parseList { parseTerm() }, id)
        "term:boolean" -> Term.Boolean(id)
        "term:byte" -> Term.Byte(id)
        "term:short" -> Term.Short(id)
        "term:int" -> Term.Int(id)
        "term:long" -> Term.Long(id)
        "term:float" -> Term.Float(id)
        "term:double" -> Term.Double(id)
        "term:string" -> Term.String(id)
        "term:byte_array" -> Term.ByteArray(id)
        "term:int_array" -> Term.IntArray(id)
        "term:long_array" -> Term.LongArray(id)
        "term:list" -> Term.List(this["element"].parseTerm(), id)
        "term:compound" -> Term.Compound(this["elements"].parseList { label to parseTerm() }, id)
        "term:function" -> Term.Function(this["parameters"].parseList { parseSubtyping() }, this["resultant"].parseTerm(), id)
        "term:code" -> Term.Code(this["element"].parseTerm(), id)
        "term:type" -> Term.Type(id)
        else -> throw Exception("malformed source")
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
