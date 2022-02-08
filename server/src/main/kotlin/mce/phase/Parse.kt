package mce.phase

import mce.graph.Id
import mce.graph.freshId
import java.util.*
import mce.graph.Surface as S

class Parse private constructor(
    private val source: String
) {
    private var cursor: Int = 0

    private fun parseItem(name: String): S.Item {
        val imports = run {
            if (readWord() != "import") error("'import' expected")
            parseList(::readWord)
        }

        return when (val word = readWord()) {
            "definition" -> {
                val modifiers = parseList(::parseModifier)
                expect(':')
                val type = parseTerm()
                expect('=')
                val body = parseTerm()
                S.Item.Definition(imports, modifiers, name, type, body)
            }
            else -> error("unexpected item '$word'")
        }
    }

    private fun parseModifier(): S.Modifier = when (val word = readWord()) {
        "meta" -> S.Modifier.META
        else -> error("unexpected modifier '$word'")
    }

    private fun parseTerm(id: Id = freshId()): S.Term = when (peekChar()) {
        '(' -> parseParen { parseTerm(it) }
        '!' -> {
            skip()
            S.Term.Definition(readWord(), id)
        }
        '"' -> S.Term.StringOf(readString(), id)
        '[' -> {
            skip()
            val char = peek()
            when {
                char == 'b' && peek(1) == ';' -> {
                    skip(2)
                    S.Term.ByteArrayOf(parseDelimitedList(']', ::parseTerm), id)
                }
                char == 'i' && peek(1) == ';' -> {
                    skip(2)
                    S.Term.IntArrayOf(parseDelimitedList(']', ::parseTerm), id)
                }
                char == 'l' && peek(1) == ';' -> {
                    skip(2)
                    S.Term.LongArrayOf(parseDelimitedList(']', ::parseTerm), id)
                }
                else -> S.Term.ListOf(parseDelimitedList(']', ::parseTerm), id)
            }
        }
        '{' -> {
            skip()
            S.Term.CompoundOf(parseDelimitedList('}', ::parseTerm), id)
        }
        '&' -> {
            skip()
            S.Term.ReferenceOf(parseTerm(), id)
        }
        '\\' -> {
            skip()
            S.Term.FunctionOf(parseList(::readWord), parseTerm(), id)
        }
        '@' -> {
            skip()
            S.Term.Apply(parseTerm(), parseList { parseTerm() }, id)
        }
        '`' -> {
            skip()
            S.Term.CodeOf(parseTerm(), id)
        }
        '$' -> {
            skip()
            S.Term.Splice(parseTerm(), id)
        }
        else -> when (val word = readWord()) {
            "hole" -> S.Term.Hole(id)
            "meta" -> S.Term.Meta(readWord().toInt(), id)
            "let" -> {
                val name = readWord()
                expect('=')
                val init = parseTerm()
                val body = parseTerm()
                S.Term.Let(name, init, body, id)
            }
            "match" -> {
                val scrutinee = parseTerm()
                expect('{')
                val clauses = parseDelimitedList('}') { parsePair(::parsePattern, { expect('='); expect('>') }, ::parseTerm) }
                S.Term.Match(scrutinee, clauses, id)
            }
            "false" -> S.Term.BooleanOf(false, id)
            "true" -> S.Term.BooleanOf(true, id)
            "union" -> S.Term.Union(parseList { parseTerm() }, id)
            "end" -> S.Term.Union(emptyList(), id)
            "intersection" -> S.Term.Intersection(parseList { parseTerm() }, id)
            "any" -> S.Term.Intersection(emptyList(), id)
            "boolean" -> S.Term.Boolean(id)
            "byte" -> S.Term.Byte(id)
            "short" -> S.Term.Short(id)
            "int" -> S.Term.Int(id)
            "long" -> S.Term.Long(id)
            "float" -> S.Term.Float(id)
            "double" -> S.Term.Double(id)
            "string" -> S.Term.String(id)
            "byte_array" -> S.Term.ByteArray(id)
            "int_array" -> S.Term.IntArray(id)
            "long_array" -> S.Term.LongArray(id)
            "list" -> S.Term.List(parseTerm(), id)
            "compound" -> {
                expect('{')
                val elements = parseDelimitedList('}') { parsePair(::readWord, { expect(':') }, ::parseTerm) }
                S.Term.Compound(elements, id)
            }
            "reference" -> S.Term.Reference(parseTerm(), id)
            "function" -> S.Term.Function(parseList { parseParen { S.Parameter(readWord(), parseTerm(), parseTerm(), parseTerm()) } }, parseTerm(), id)
            "code" -> S.Term.Code(parseTerm(), id)
            "type" -> S.Term.Type(id)
            else -> when {
                BYTE_EXPRESSION.matches(word) -> S.Term.ByteOf(word.dropLast(1).toByte(), id)
                SHORT_EXPRESSION.matches(word) -> S.Term.ShortOf(word.dropLast(1).toShort(), id)
                INT_EXPRESSION.matches(word) -> S.Term.IntOf(word.toInt(), id)
                LONG_EXPRESSION.matches(word) -> S.Term.LongOf(word.dropLast(1).toLong(), id)
                FLOAT_EXPRESSION.matches(word) -> S.Term.FloatOf(word.dropLast(1).toFloat(), id)
                DOUBLE_EXPRESSION.matches(word) -> S.Term.DoubleOf(word.dropLast(1).toDouble(), id)
                else -> S.Term.Variable(word, id)
            }
        }
    }

    private fun parsePattern(id: Id = freshId()): S.Pattern = when (peekChar()) {
        '(' -> parseParen { parsePattern(it) }
        '"' -> S.Pattern.StringOf(readString(), id)
        '[' -> {
            skip()
            val char = peek()
            when {
                char == 'b' && peek(1) == ';' -> {
                    skip(2)
                    S.Pattern.ByteArrayOf(parseDelimitedList(']', ::parsePattern), id)
                }
                char == 'i' && peek(1) == ';' -> {
                    skip(2)
                    S.Pattern.IntArrayOf(parseDelimitedList(']', ::parsePattern), id)
                }
                char == 'l' && peek(1) == ';' -> {
                    skip(2)
                    S.Pattern.LongArrayOf(parseDelimitedList(']', ::parsePattern), id)
                }
                else -> S.Pattern.ListOf(parseDelimitedList(']', ::parsePattern), id)
            }
        }
        '{' -> {
            skip()
            S.Pattern.CompoundOf(parseDelimitedList('}', ::parsePattern), id)
        }
        '&' -> {
            skip()
            S.Pattern.ReferenceOf(parsePattern(), id)
        }
        else -> when (val word = readWord()) {
            "false" -> S.Pattern.BooleanOf(false, id)
            "true" -> S.Pattern.BooleanOf(true, id)
            else -> when {
                BYTE_EXPRESSION.matches(word) -> S.Pattern.ByteOf(word.dropLast(1).toByte(), id)
                SHORT_EXPRESSION.matches(word) -> S.Pattern.ShortOf(word.dropLast(1).toShort(), id)
                INT_EXPRESSION.matches(word) -> S.Pattern.IntOf(word.toInt(), id)
                LONG_EXPRESSION.matches(word) -> S.Pattern.LongOf(word.dropLast(1).toLong(), id)
                FLOAT_EXPRESSION.matches(word) -> S.Pattern.FloatOf(word.dropLast(1).toFloat(), id)
                DOUBLE_EXPRESSION.matches(word) -> S.Pattern.DoubleOf(word.dropLast(1).toDouble(), id)
                else -> S.Pattern.Variable(word, id)
            }
        }
    }

    private fun parseEffect(): S.Effect = TODO()

    private inline fun <A> parseParen(parseA: (Id) -> A): A {
        expect('(')
        return when (peekChar()) {
            '#' -> {
                skip()
                val explicitId = UUID.fromString(readWord())
                parseA(explicitId)
            }
            else -> parseA(freshId())
        }.also { expect(')') }
    }

    private inline fun <A, B> parsePair(parseA: () -> A, delimit: () -> Unit, parseB: () -> B): Pair<A, B> {
        val a = parseA()
        delimit()
        val b = parseB()
        return a to b
    }

    private inline fun <A> parseList(parseA: () -> A): List<A> = parseParen {
        mutableListOf<A>().also {
            while (peekChar() != ')') it += parseA()
        }
    }

    private inline fun <A> parseDelimitedList(end: Char, parseA: () -> A): List<A> = mutableListOf<A>().also {
        while (peekChar() != end) {
            it += parseA()
            expect(',')
        }
        expect(end)
    }

    private fun canRead(length: Int = 1): Boolean = cursor + length <= source.length

    private fun peek(offset: Int = 0): Char = source[cursor + offset]

    private fun skip(size: Int = 1) {
        cursor += size
    }

    private fun skipWhitespace() {
        while (canRead() && peek().isWhitespace()) skip()
    }

    private fun expect(char: Char) {
        skipWhitespace()
        if (!canRead() || peek() != char) error("'$char' expected but '${peek()}' found")
        skip()
    }

    private fun peekChar(): Char {
        skipWhitespace()
        return peek()
    }

    private fun readWord(): String {
        skipWhitespace()
        val start = cursor
        while (canRead() && peek().isWordPart()) skip()
        return source.substring(start, cursor).also { if (it.isEmpty()) error("word expected") }
    }

    // TODO: handle escape
    private fun readString(): String {
        expect('"')
        val start = cursor
        while (canRead() && peek() != '"') skip()
        return source.substring(start, cursor).also { skip() }
    }

    private fun Char.isWordPart(): Boolean = !this.isWhitespace() && this != ',' && this != '(' && this != ')' && this != '[' && this != ']' && this != '{' && this != '}' && this != '='

    private fun error(message: String): Nothing = throw Error("$message at $cursor")

    companion object {
        private val BYTE_EXPRESSION = Regex("[-+]?(?:0|[1-9][0-9]*)b")
        private val SHORT_EXPRESSION = Regex("[-+]?(?:0|[1-9][0-9]*)s")
        private val INT_EXPRESSION = Regex("[-+]?(?:0|[1-9][0-9]*)")
        private val LONG_EXPRESSION = Regex("[-+]?(?:0|[1-9][0-9]*)l")
        private val FLOAT_EXPRESSION = Regex("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?f")
        private val DOUBLE_EXPRESSION = Regex("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?d")

        operator fun invoke(name: String, input: String): S.Item = Parse(input).parseItem(name)
    }
}
