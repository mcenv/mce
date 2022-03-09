package mce.phase.frontend

import mce.graph.Id
import mce.graph.Name
import mce.graph.freshId
import java.util.*
import mce.graph.Surface as S

class Parse private constructor(
    private val source: String
) {
    private var cursor: Int = 0

    private fun parseItem(id: Id = freshId()): S.Item {
        var word = readWord()
        val imports = if (word == "import") parseList('{', '}') { readWord() }.also { word = readWord() } else emptyList()
        val exports = if (word == "export") parseList('{', '}') { readWord() }.also { word = readWord() } else emptyList()
        return when (word) {
            "def" -> {
                val name = readWord()
                val modifiers = if (peekChar() == '{') parseList('{', '}') { parseModifier() } else emptyList()
                val parameters = parseList('[', ']') { parseParameter() }
                val resultant = run {
                    expect(':')
                    parseTerm()
                }
                val effects = if (peekChar() == '!') {
                    skip()
                    parseEffects()
                } else emptyList()
                val body = run {
                    expectString("≔")
                    parseTerm()
                }
                S.Item.Def(imports, exports, modifiers, name, parameters, resultant, effects, body, id)
            }
            "mod" -> {
                val name = readWord()
                val modifiers = if (peekChar() == '{') parseList('{', '}') { parseModifier() } else emptyList()
                expect(':')
                val type = parseModule()
                expectString("≔")
                val body = parseModule()
                S.Item.Mod(imports, exports, modifiers, name, type, body, id)
            }
            else -> error("unexpected item '$word'")
        }
    }

    private fun parseModifier(): S.Modifier = when (val word = readWord()) {
        "builtin" -> S.Modifier.BUILTIN
        "meta" -> S.Modifier.META
        else -> error("unexpected modifier '$word'")
    }

    private fun parseParameter(id: Id = freshId()): S.Parameter {
        val erased = if (peekChar() == '0') {
            skip()
            false
        } else true
        val name = parseTerm()
        return if (peekChar() == ',' || peekChar() == ']') {
            S.Parameter(erased, "", null, null, name, id)
        } else {
            val lower = if (peekChar() == '≥') {
                skip()
                parseTerm()
            } else null
            val upper = if (peekChar() == '≤') {
                skip()
                parseTerm()
            } else null
            val type = run {
                expect(':')
                parseTerm()
            }
            S.Parameter(erased, (name as S.Term.Var).name, lower, upper, type, id)
        }
    }

    private fun parseModule(id: Id = freshId()): S.Module = when (peekChar()) {
        '{' -> {
            val items = parseList('{', '}') { parseItem() }
            S.Module.Str(items, id)
        }
        else -> when (val word = readWord()) {
            "sig" -> {
                val signatures = parseList('{', '}') { parseSignature() }
                S.Module.Sig(signatures, id)
            }
            "type" -> S.Module.Type(id)
            else -> S.Module.Var(word, id)
        }
    }

    private fun parseSignature(id: Id = freshId()): S.Signature = when (val word = readWord()) {
        "def" -> {
            val name = readWord()
            val parameters = parseList('[', ']') { parseParameter() }
            val resultant = run {
                expect(':')
                parseTerm()
            }
            S.Signature.Def(name, parameters, resultant, id)
        }
        "mod" -> {
            val name = readWord()
            expect(':')
            val type = parseModule()
            S.Signature.Mod(name, type, id)
        }
        else -> error("unexpected signature '$word'")
    }

    private fun parseTerm(id: Id = freshId()): S.Term = when (peekChar()) {
        '(' -> {
            skip()
            if (peekChar() == '#') {
                skip()
                parseTerm(UUID.fromString(readWord()))
            } else {
                val left = parseTerm()
                when (peekChar()) {
                    ')' -> left
                    '[' -> {
                        val arguments = parseList('[', ']') { parseTerm() }
                        S.Term.Def((left as? S.Term.Var)?.name ?: error("name expected"), arguments, id)
                    }
                    '@' -> {
                        skip()
                        val arguments = parseList('[', ']') { parseTerm() }
                        S.Term.Apply(left, arguments, id)
                    }
                    ':' -> {
                        skip()
                        val right = parseTerm()
                        S.Term.Anno(left, right, id)
                    }
                    '∈' -> {
                        skip()
                        val right = parseTerm()
                        S.Term.BoxOf(left, right, id)
                    }
                    '=' -> {
                        skip()
                        val right = parseTerm()
                        S.Term.Eq(left, right, id)
                    }
                    else -> {
                        val operator = parseTerm()
                        val right = parseTerm()
                        S.Term.Def((operator as? S.Term.Var)?.name ?: error("name expected"), listOf(left, right), id)
                    }
                }
            }.also { expect(')') }
        }
        '_' -> {
            skip()
            S.Term.Hole(id)
        }
        '?' -> {
            skip()
            S.Term.Meta(id)
        }
        '⟨' -> {
            skip()
            S.Term.UnitOf(id).also { expect('⟩') }
        }
        '"' -> S.Term.StringOf(readString(), id)
        '[' -> when {
            peek(1) == 'b' && peek(2) == ';' -> {
                skip(2)
                S.Term.ByteArrayOf(parseList(';', ']') { parseTerm() }, id)
            }
            peek(1) == 'i' && peek(2) == ';' -> {
                skip(2)
                S.Term.IntArrayOf(parseList(';', ']') { parseTerm() }, id)
            }
            peek(1) == 'l' && peek(2) == ';' -> {
                skip(2)
                S.Term.LongArrayOf(parseList(';', ']') { parseTerm() }, id)
            }
            else -> S.Term.ListOf(parseList('[', ']') { parseTerm() }, id)
        }
        '{' -> S.Term.CompoundOf(parseList('{', '}') { parsePair({ parseName() }, { expect(':') }, { parseTerm() }) }, id)
        '&' -> {
            skip()
            S.Term.RefOf(parseTerm(), id)
        }
        'λ' -> {
            skip()
            val parameters = parseList('[', ']') { parseName() }
            expectString("→")
            val body = parseTerm()
            S.Term.FunOf(parameters, body, id)
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
            "let" -> {
                val name = readWord()
                expectString("≔")
                val init = parseTerm()
                expect(',')
                val body = parseTerm()
                S.Term.Let(name, init, body, id)
            }
            "match" -> {
                val scrutinee = parseTerm()
                val clauses = parseList('[', ']') { parsePair({ parsePattern() }, { expectString("⇒") }, { parseTerm() }) }
                S.Term.Match(scrutinee, clauses, id)
            }
            "false" -> S.Term.BoolOf(false, id)
            "true" -> S.Term.BoolOf(true, id)
            "refl" -> S.Term.Refl(id)
            "union" -> {
                val variants = parseList('{', '}') { parseTerm() }
                S.Term.Union(variants, id)
            }
            "end" -> S.Term.Union(emptyList(), id)
            "intersection" -> {
                val variants = parseList('{', '}') { parseTerm() }
                S.Term.Intersection(variants, id)
            }
            "any" -> S.Term.Intersection(emptyList(), id)
            "unit" -> S.Term.Unit(id)
            "bool" -> S.Term.Bool(id)
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
            "list" -> {
                val element = parseTerm()
                val size = parseTerm()
                S.Term.List(element, size, id)
            }
            "compound" -> {
                val elements = parseList('{', '}') {
                    val left = parseTerm()
                    if (peekChar() == ',' || peekChar() == '}') {
                        Name("", freshId()) to left
                    } else {
                        expect(':')
                        val right = parseTerm()
                        Name((left as? S.Term.Var)?.name ?: error("name expected"), freshId()) to right
                    }
                }
                S.Term.Compound(elements, id)
            }
            "box" -> {
                val content = parseTerm()
                S.Term.Box(content, id)
            }
            "ref" -> S.Term.Ref(parseTerm(), id)
            "fun" -> {
                val parameters = parseList('[', ']') { parseParameter() }
                expectString("→")
                val resultant = parseTerm()
                val effects = if (peekChar() == '!') {
                    skip()
                    parseEffects()
                } else emptyList()
                S.Term.Fun(parameters, resultant, effects, id)
            }
            "code" -> S.Term.Code(parseTerm(), id)
            "type" -> S.Term.Type(id)
            else -> when {
                BYTE_EXPRESSION.matches(word) -> S.Term.ByteOf(word.dropLast(1).toByte(), id)
                SHORT_EXPRESSION.matches(word) -> S.Term.ShortOf(word.dropLast(1).toShort(), id)
                INT_EXPRESSION.matches(word) -> S.Term.IntOf(word.toInt(), id)
                LONG_EXPRESSION.matches(word) -> S.Term.LongOf(word.dropLast(1).toLong(), id)
                FLOAT_EXPRESSION.matches(word) -> S.Term.FloatOf(word.dropLast(1).toFloat(), id)
                DOUBLE_EXPRESSION.matches(word) -> S.Term.DoubleOf(word.dropLast(1).toDouble(), id)
                else -> S.Term.Var(word, id)
            }
        }
    }

    private fun parsePattern(id: Id = freshId()): S.Pattern = when (peekChar()) {
        '(' -> {
            skip()
            val left = parsePattern()
            when (val char = peekChar()) {
                ')' -> left
                '∈' -> {
                    skip()
                    val right = parsePattern()
                    S.Pattern.BoxOf(left, right, id)
                }
                else -> error("unexpected operator '$char'")
            }.also { expect(')') }
        }
        '⟨' -> {
            skip()
            S.Pattern.UnitOf(id).also { expect('⟩') }
        }
        '"' -> S.Pattern.StringOf(readString(), id)
        '[' -> when {
            peek(1) == 'b' && peek(2) == ';' -> {
                skip(2)
                S.Pattern.ByteArrayOf(parseList(';', ']') { parsePattern() }, id)
            }
            peek(1) == 'i' && peek(2) == ';' -> {
                skip(2)
                S.Pattern.IntArrayOf(parseList(';', ']') { parsePattern() }, id)
            }
            peek(1) == 'l' && peek(2) == ';' -> {
                skip(2)
                S.Pattern.LongArrayOf(parseList(';', ']') { parsePattern() }, id)
            }
            else -> S.Pattern.ListOf(parseList('[', ']') { parsePattern() }, id)
        }
        '{' -> S.Pattern.CompoundOf(parseList('{', '}') { parsePair({ parseName() }, { expect(':') }, { parsePattern() }) }, id)
        '&' -> {
            skip()
            S.Pattern.RefOf(parsePattern(), id)
        }
        else -> when (val word = readWord()) {
            "false" -> S.Pattern.BoolOf(false, id)
            "true" -> S.Pattern.BoolOf(true, id)
            "refl" -> S.Pattern.Refl(id)
            "unit" -> S.Pattern.Unit(id)
            "bool" -> S.Pattern.Bool(id)
            "byte" -> S.Pattern.Byte(id)
            "short" -> S.Pattern.Short(id)
            "int" -> S.Pattern.Int(id)
            "long" -> S.Pattern.Long(id)
            "float" -> S.Pattern.Float(id)
            "double" -> S.Pattern.Double(id)
            "string" -> S.Pattern.String(id)
            "byte_array" -> S.Pattern.ByteArray(id)
            "int_array" -> S.Pattern.IntArray(id)
            "long_array" -> S.Pattern.LongArray(id)
            else -> when {
                BYTE_EXPRESSION.matches(word) -> S.Pattern.ByteOf(word.dropLast(1).toByte(), id)
                SHORT_EXPRESSION.matches(word) -> S.Pattern.ShortOf(word.dropLast(1).toShort(), id)
                INT_EXPRESSION.matches(word) -> S.Pattern.IntOf(word.toInt(), id)
                LONG_EXPRESSION.matches(word) -> S.Pattern.LongOf(word.dropLast(1).toLong(), id)
                FLOAT_EXPRESSION.matches(word) -> S.Pattern.FloatOf(word.dropLast(1).toFloat(), id)
                DOUBLE_EXPRESSION.matches(word) -> S.Pattern.DoubleOf(word.dropLast(1).toDouble(), id)
                else -> S.Pattern.Var(word, id)
            }
        }
    }

    private fun parseEffects(): List<S.Effect> = parseList('{', '}') { parseEffect() }

    private fun parseEffect(): S.Effect {
        val name = readWord()
        return S.Effect.Name(name)
    }

    private fun parseName(): Name {
        val name = readWord()
        return Name(name, freshId())
    }

    private inline fun <A, B> parsePair(parseA: () -> A, delimit: () -> Unit, parseB: () -> B): Pair<A, B> {
        val a = parseA()
        delimit()
        val b = parseB()
        return a to b
    }

    private inline fun <A> parseList(begin: Char, end: Char, parseA: () -> A): List<A> = mutableListOf<A>().also {
        expect(begin)
        while (peekChar() != end) {
            it += parseA()
            if (peekChar() == ',') skip() else break
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

    private fun expectString(string: String) {
        skipWhitespace()
        if (!canRead(string.length) || !source.substring(cursor).startsWith(string)) error("'$string' expected")
        skip(string.length)
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

    private fun Char.isWordPart(): Boolean = this != ',' && this != ')' && this != ']' && this != '}' && this != ':' && !this.isWhitespace()

    private fun error(message: String): Nothing = throw Error("$message at $cursor")

    companion object {
        private val BYTE_EXPRESSION = Regex("[-+]?(?:0|[1-9][0-9]*)b")
        private val SHORT_EXPRESSION = Regex("[-+]?(?:0|[1-9][0-9]*)s")
        private val INT_EXPRESSION = Regex("[-+]?(?:0|[1-9][0-9]*)")
        private val LONG_EXPRESSION = Regex("[-+]?(?:0|[1-9][0-9]*)l")
        private val FLOAT_EXPRESSION = Regex("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?f")
        private val DOUBLE_EXPRESSION = Regex("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?d")

        operator fun invoke(name: String, input: String): S.Item = Parse(input).run {
            parseItem().also {
                if (it.name != name) error("")
                skipWhitespace()
                if (canRead()) error("end of file expected")
            }
        }
    }
}
