package mce.phase

import mce.graph.freshId
import mce.graph.Surface as S

class Parse private constructor(
    private val source: String
) {
    private var cursor: Int = 0

    private fun parseItem(): S.Item = parseParen {
        when (val word = readWord()) {
            "definition" -> S.Item.Definition(freshId(), readWord(), parseList(::readWord), readWord().toBooleanStrict(), parseTerm(), parseTerm())
            else -> throw Error("'$word' unexpected")
        }
    }

    private fun parseTerm(): S.Term = when (run { skipWhitespace(); peek() }) {
        '(' -> parseParen(::parseTerm)
        '"' -> S.Term.StringOf(readString(), freshId())
        else -> when (val word = readWord()) {
            "hole" -> S.Term.Hole(freshId())
            "meta" -> S.Term.Meta(readWord().toInt(), freshId())
            "definition" -> S.Term.Definition(readWord(), freshId())
            "let" -> S.Term.Let(readWord(), parseTerm(), parseTerm(), freshId())
            "match" -> S.Term.Match(parseTerm(), parseList { parsePair(::parsePattern, ::parseTerm) }, freshId())
            "false" -> S.Term.BooleanOf(false, freshId())
            "true" -> S.Term.BooleanOf(true, freshId())
            "byte_array_of" -> S.Term.ByteArrayOf(parseList(::parseTerm), freshId())
            "int_array_of" -> S.Term.IntArrayOf(parseList(::parseTerm), freshId())
            "long_array_of" -> S.Term.LongArrayOf(parseList(::parseTerm), freshId())
            "list_of" -> S.Term.ListOf(parseList(::parseTerm), freshId())
            "compound_of" -> S.Term.CompoundOf(parseList(::parseTerm), freshId())
            "reference_of" -> S.Term.ReferenceOf(parseTerm(), freshId())
            "function_of" -> S.Term.FunctionOf(parseList(::readWord), parseTerm(), freshId())
            "apply" -> S.Term.Apply(parseTerm(), parseList(::parseTerm), freshId())
            "code_of" -> S.Term.CodeOf(parseTerm(), freshId())
            "splice" -> S.Term.Splice(parseTerm(), freshId())
            "union" -> S.Term.Union(parseList(::parseTerm), freshId())
            "intersection" -> S.Term.Intersection(parseList(::parseTerm), freshId())
            "boolean" -> S.Term.Boolean(freshId())
            "byte" -> S.Term.Byte(freshId())
            "short" -> S.Term.Short(freshId())
            "int" -> S.Term.Int(freshId())
            "long" -> S.Term.Long(freshId())
            "float" -> S.Term.Float(freshId())
            "double" -> S.Term.Double(freshId())
            "string" -> S.Term.String(freshId())
            "byte_array" -> S.Term.ByteArray(freshId())
            "int_array" -> S.Term.IntArray(freshId())
            "long_array" -> S.Term.LongArray(freshId())
            "list" -> S.Term.List(parseTerm(), freshId())
            "compound" -> S.Term.Compound(parseList { parsePair(::readWord, ::parseTerm) }, freshId())
            "reference" -> S.Term.Reference(parseTerm(), freshId())
            "function" -> S.Term.Function(parseList { parseParen { S.Parameter(readWord(), parseTerm(), parseTerm(), parseTerm()) } }, parseTerm(), freshId())
            "code" -> S.Term.Code(parseTerm(), freshId())
            "type" -> S.Term.Type(freshId())
            else -> when {
                BYTE_EXPRESSION.matches(word) -> S.Term.ByteOf(word.dropLast(1).toByte(), freshId())
                SHORT_EXPRESSION.matches(word) -> S.Term.ShortOf(word.dropLast(1).toShort(), freshId())
                INT_EXPRESSION.matches(word) -> S.Term.IntOf(word.toInt(), freshId())
                LONG_EXPRESSION.matches(word) -> S.Term.LongOf(word.dropLast(1).toLong(), freshId())
                FLOAT_EXPRESSION.matches(word) -> S.Term.FloatOf(word.dropLast(1).toFloat(), freshId())
                DOUBLE_EXPRESSION.matches(word) -> S.Term.DoubleOf(word.dropLast(1).toDouble(), freshId())
                else -> {
                    println("'$word'"); TODO(); S.Term.Variable(word, TODO(), freshId())
                }
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
        if (!canRead() || peek() != char) throw Error("'$char' expected but '${peek()}' found")
        skip()
    }

    private fun readWord(): String {
        skipWhitespace()
        val start = cursor
        while (canRead() && peek().let { it != ' ' && it != '(' && it != ')' }) skip()
        return source.substring(start, cursor).also { if (it.isEmpty()) throw Error("$cursor") }
    }

    // TODO: handle escape
    private fun readString(): String {
        expect('"')
        val start = cursor
        while (canRead() && peek() != '"') skip()
        return source.substring(start, cursor).also { skip() }
    }

    companion object : Phase<String, S.Item> {
        private val BYTE_EXPRESSION = Regex("[-+]?(?:0|[1-9][0-9]*)b")
        private val SHORT_EXPRESSION = Regex("[-+]?(?:0|[1-9][0-9]*)s")
        private val INT_EXPRESSION = Regex("[-+]?(?:0|[1-9][0-9]*)")
        private val LONG_EXPRESSION = Regex("[-+]?(?:0|[1-9][0-9]*)l")
        private val FLOAT_EXPRESSION = Regex("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?f")
        private val DOUBLE_EXPRESSION = Regex("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?d")

        override operator fun invoke(input: String): S.Item = Parse(input).parseItem()
    }
}
