package mce.phase

import mce.graph.freshId
import java.util.*
import mce.graph.Surface as S

class Parse private constructor(
    private val source: String
) {
    private var cursor: Int = 0

    private fun parseItem(): S.Item = parseParen {
        when (readWord()) {
            "definition" -> S.Item.Definition(freshId(), readWord(), parseList(::readWord), readWord().toBooleanStrict(), parseTerm(), parseTerm())
            else -> throw Error()
        }
    }

    private fun parseTerm(id: () -> UUID = ::freshId): S.Term = when (run { skipWhitespace(); peek() }) {
        '(' -> parseParen { parseTerm() }
        '"' -> S.Term.StringOf(readString(), id())
        '#' -> {
            skip()
            val explicitId = UUID.fromString(readWord())
            parseTerm { explicitId }
        }
        else -> when (val word = readWord()) {
            "hole" -> S.Term.Hole(id())
            "meta" -> S.Term.Meta(readWord().toInt(), id())
            "definition" -> S.Term.Definition(readWord(), id())
            "let" -> S.Term.Let(readWord(), parseTerm(), parseTerm(), id())
            "match" -> S.Term.Match(parseTerm(), parseList { parsePair(::parsePattern, ::parseTerm) }, id())
            "false" -> S.Term.BooleanOf(false, id())
            "true" -> S.Term.BooleanOf(true, id())
            "byte_array_of" -> S.Term.ByteArrayOf(parseList { parseTerm() }, id())
            "int_array_of" -> S.Term.IntArrayOf(parseList { parseTerm() }, id())
            "long_array_of" -> S.Term.LongArrayOf(parseList { parseTerm() }, id())
            "list_of" -> S.Term.ListOf(parseList { parseTerm() }, id())
            "compound_of" -> S.Term.CompoundOf(parseList { parseTerm() }, id())
            "reference_of" -> S.Term.ReferenceOf(parseTerm(), id())
            "function_of" -> S.Term.FunctionOf(parseList(::readWord), parseTerm(), id())
            "apply" -> S.Term.Apply(parseTerm(), parseList { parseTerm() }, id())
            "code_of" -> S.Term.CodeOf(parseTerm(), id())
            "splice" -> S.Term.Splice(parseTerm(), id())
            "union" -> S.Term.Union(parseList { parseTerm() }, id())
            "intersection" -> S.Term.Intersection(parseList { parseTerm() }, id())
            "boolean" -> S.Term.Boolean(id())
            "byte" -> S.Term.Byte(id())
            "short" -> S.Term.Short(id())
            "int" -> S.Term.Int(id())
            "long" -> S.Term.Long(id())
            "float" -> S.Term.Float(id())
            "double" -> S.Term.Double(id())
            "string" -> S.Term.String(id())
            "byte_array" -> S.Term.ByteArray(id())
            "int_array" -> S.Term.IntArray(id())
            "long_array" -> S.Term.LongArray(id())
            "list" -> S.Term.List(parseTerm(), id())
            "compound" -> S.Term.Compound(parseList { parsePair(::readWord, ::parseTerm) }, id())
            "reference" -> S.Term.Reference(parseTerm(), id())
            "function" -> S.Term.Function(parseList { parseParen { S.Parameter(readWord(), parseTerm(), parseTerm(), parseTerm()) } }, parseTerm(), id())
            "code" -> S.Term.Code(parseTerm(), id())
            "type" -> S.Term.Type(id())
            else -> when {
                BYTE_EXPRESSION.matches(word) -> S.Term.ByteOf(word.dropLast(1).toByte(), id())
                SHORT_EXPRESSION.matches(word) -> S.Term.ShortOf(word.dropLast(1).toShort(), id())
                INT_EXPRESSION.matches(word) -> S.Term.IntOf(word.toInt(), id())
                LONG_EXPRESSION.matches(word) -> S.Term.LongOf(word.dropLast(1).toLong(), id())
                FLOAT_EXPRESSION.matches(word) -> S.Term.FloatOf(word.dropLast(1).toFloat(), id())
                DOUBLE_EXPRESSION.matches(word) -> S.Term.DoubleOf(word.dropLast(1).toDouble(), id())
                else -> S.Term.Variable(word, TODO(), id())
            }
        }
    }

    private fun parsePattern(): S.Pattern = when (run { skipWhitespace(); peek() }) {
        '(' -> parseParen(::parsePattern)
        '"' -> S.Pattern.StringOf(readString(), freshId())
        else -> when (val word = readWord()) {
            "false" -> S.Pattern.BooleanOf(false, freshId())
            "true" -> S.Pattern.BooleanOf(true, freshId())
            "byte_array_of" -> S.Pattern.ByteArrayOf(parseList(::parsePattern), freshId())
            "int_array_of" -> S.Pattern.IntArrayOf(parseList(::parsePattern), freshId())
            "long_array_of" -> S.Pattern.LongArrayOf(parseList(::parsePattern), freshId())
            "list_of" -> S.Pattern.ListOf(parseList(::parsePattern), freshId())
            "compound_of" -> S.Pattern.CompoundOf(parseList(::parsePattern), freshId())
            "reference_of" -> S.Pattern.ReferenceOf(parsePattern(), freshId())
            else -> when {
                BYTE_EXPRESSION.matches(word) -> S.Pattern.ByteOf(word.dropLast(1).toByte(), freshId())
                SHORT_EXPRESSION.matches(word) -> S.Pattern.ShortOf(word.dropLast(1).toShort(), freshId())
                INT_EXPRESSION.matches(word) -> S.Pattern.IntOf(word.toInt(), freshId())
                LONG_EXPRESSION.matches(word) -> S.Pattern.LongOf(word.dropLast(1).toLong(), freshId())
                FLOAT_EXPRESSION.matches(word) -> S.Pattern.FloatOf(word.dropLast(1).toFloat(), freshId())
                DOUBLE_EXPRESSION.matches(word) -> S.Pattern.DoubleOf(word.dropLast(1).toDouble(), freshId())
                else -> S.Pattern.Variable(word, freshId())
            }
        }
    }

    private inline fun <A> parseParen(parseA: () -> A): A {
        expect('(')
        return parseA().also { expect(')') }
    }

    private inline fun <A, B> parsePair(parseA: () -> A, parseB: () -> B): Pair<A, B> = parseParen { parseA() to parseB() }

    private inline fun <A> parseList(parseA: () -> A): List<A> = parseParen {
        mutableListOf<A>().also {
            while (peek() != ')') it += parseA()
        }
    }

    private fun canRead(length: Int = 1): Boolean = cursor + length <= source.length

    private fun peek(): Char = source[cursor]

    private fun skip() {
        ++cursor
    }

    private fun skipWhitespace() {
        while (canRead() && peek().isWhitespace()) skip()
    }

    private fun expect(char: Char) {
        skipWhitespace()
        if (!canRead() || peek() != char) throw Error()
        skip()
    }

    private fun readWord(): String {
        skipWhitespace()
        val start = cursor
        while (canRead() && peek().let { it != ' ' && it != '(' && it != ')' }) skip()
        return source.substring(start, cursor).also { if (it.isEmpty()) throw Error() }
    }

    // TODO: handle escape
    private fun readString(): String {
        expect('"')
        val start = cursor
        while (canRead() && peek() != '"') skip()
        return source.substring(start, cursor).also { skip() }
    }

    companion object {
        private val BYTE_EXPRESSION = Regex("[-+]?(?:0|[1-9][0-9]*)b")
        private val SHORT_EXPRESSION = Regex("[-+]?(?:0|[1-9][0-9]*)s")
        private val INT_EXPRESSION = Regex("[-+]?(?:0|[1-9][0-9]*)")
        private val LONG_EXPRESSION = Regex("[-+]?(?:0|[1-9][0-9]*)l")
        private val FLOAT_EXPRESSION = Regex("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?f")
        private val DOUBLE_EXPRESSION = Regex("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?d")

        operator fun invoke(input: String): S.Item = Parse(input).parseItem()
    }
}
