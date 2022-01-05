package mce.phase

import mce.graph.Surface.Term
import java.util.*

class Parse(source: String) {
    private val lines: Iterator<String> = source.lineSequence().iterator()
    private val regex: Regex = Regex("""([ ]*)([a-z_]+) ([a-z_]+) ([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}) ([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?: (.*))?""")

    fun parseTerm(): Term {
        val line = lines.next()
        val values = regex.matchEntire(line)!!.groupValues
        val indent = values[1].length
        val label = values[2]
        val type = values[3]
        val id = UUID.fromString(values[4])
        val parent = UUID.fromString(values[5])
        val value = values.getOrNull(6)
        return when (type) {
            "hole" -> Term.Hole(id)
            "meta" -> Term.Meta(value!!.toInt(), id)
            "variable" -> TODO()
            "definition" -> Term.Definition(value!!, id)
            "let" -> Term.Let(value!!, parseTerm(), parseTerm(), id)
            "boolean_of" -> Term.BooleanOf(value!!.toBooleanStrict(), id)
            "byte_of" -> Term.ByteOf(value!!.toByte(), id)
            "short_of" -> Term.ShortOf(value!!.toShort(), id)
            "int_of" -> Term.IntOf(value!!.toInt(), id)
            "long_of" -> Term.LongOf(value!!.toLong(), id)
            "float_of" -> Term.FloatOf(value!!.toFloat(), id)
            "double_of" -> Term.DoubleOf(value!!.toDouble(), id)
            "string_of" -> Term.StringOf(value!!, id)
            "byte_array_of" -> Term.ByteArrayOf(parseTerms(), id)
            "int_array_of" -> Term.IntArrayOf(parseTerms(), id)
            "long_array_of" -> Term.LongArrayOf(parseTerms(), id)
            "list_of" -> Term.ListOf(parseTerms(), id)
            "compound_of" -> Term.CompoundOf(parseTerms(), id)
            "function_of" -> TODO()
            "apply" -> Term.Apply(parseTerm(), parseTerms(), id)
            "code_of" -> Term.CodeOf(parseTerm(), id)
            "splice" -> Term.Splice(parseTerm(), id)
            "union" -> Term.Union(parseTerms(), id)
            "intersection" -> Term.Intersection(parseTerms(), id)
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
            "list" -> Term.List(parseTerm(), id)
            "compound" -> TODO()
            "function" -> TODO()
            "code" -> Term.Code(parseTerm(), id)
            "type" -> Term.Type(id)
            else -> throw Exception("malformed source")
        }
    }

    private fun parseTerms(): List<Term> = TODO()
}
