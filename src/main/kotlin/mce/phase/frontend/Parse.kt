package mce.phase.frontend

import mce.ast.Id
import mce.ast.Name
import mce.ast.freshId
import mce.ast.surface.*
import java.util.*

@Deprecated("Use decoder")
class Parse private constructor(
    private val source: String
) {
    private var cursor: Int = 0

    private fun parseItem(id: Id = freshId()): Item {
        val modifiers = if (peekChar() == '{') parseList('{', '}') { parseModifier() } else emptyList()
        var word = readWord()
        val imports = if (word == "import") parseList('{', '}') { readWord() }.also { word = readWord() } else emptyList()
        val exports = if (word == "export") parseList('{', '}') { readWord() }.also { word = readWord() } else emptyList()
        return when (word) {
            "def" -> {
                val name = readWord()
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
                Item.Def(imports, exports, modifiers, name, parameters, resultant, effects, body, id)
            }
            "mod" -> {
                val name = readWord()
                expect(':')
                val type = parseModule()
                expectString("≔")
                val body = parseModule()
                Item.Mod(imports, exports, modifiers, name, type, body, id)
            }
            "test" -> {
                val name = readWord()
                val body = run {
                    expectString("≔")
                    parseTerm()
                }
                Item.Test(imports, exports, modifiers, name, body, id)
            }
            else -> error("unexpected item '$word'")
        }
    }

    private fun parseModifier(): Modifier = when (val word = readWord()) {
        "builtin" -> Modifier.BUILTIN
        "meta" -> Modifier.META
        else -> error("unexpected modifier '$word'")
    }

    private fun parseParameter(id: Id = freshId()): Parameter {
        return if (peekChar() == '0') {
            skip()
            val name = readWord()
            val lower = if (peekChar() == '≥') {
                skip()
                parseTerm()
            } else null
            val upper = if (peekChar() == '≤') {
                skip()
                parseTerm()
            } else null
            val (typeRelevant, type) = run {
                expect(':')
                (if (peekChar() == '0') {
                    skip()
                    false
                } else true) to parseTerm()
            }
            Parameter(false, name, lower, upper, typeRelevant, type, id)
        } else {
            val term = parseTerm()
            return if (peekChar() == ',' || peekChar() == ']') {
                Parameter(true, "", null, null, true, term, id)
            } else {
                val lower = if (peekChar() == '≥') {
                    skip()
                    parseTerm()
                } else null
                val upper = if (peekChar() == '≤') {
                    skip()
                    parseTerm()
                } else null
                val (typeRelevant, type) = run {
                    expect(':')
                    (if (peekChar() == '0') {
                        skip()
                        false
                    } else true) to parseTerm()
                }
                Parameter(true, (term as Term.Var).name, lower, upper, typeRelevant, type, id)
            }
        }
    }

    private fun parseModule(id: Id = freshId()): Module = when (peekChar()) {
        '{' -> {
            val items = parseList('{', '}') { parseItem() }
            Module.Str(items, id)
        }
        else -> when (val word = readWord()) {
            "sig" -> {
                val signatures = parseList('{', '}') { parseSignature() }
                Module.Sig(signatures, id)
            }
            "type" -> Module.Type(id)
            else -> Module.Var(word, id)
        }
    }

    private fun parseSignature(id: Id = freshId()): Signature = when (val word = readWord()) {
        "def" -> {
            val name = readWord()
            val parameters = parseList('[', ']') { parseParameter() }
            val resultant = run {
                expect(':')
                parseTerm()
            }
            Signature.Def(name, parameters, resultant, id)
        }
        "mod" -> {
            val name = readWord()
            expect(':')
            val type = parseModule()
            Signature.Mod(name, type, id)
        }
        "test" -> {
            val name = readWord()
            Signature.Test(name, id)
        }
        else -> error("unexpected signature '$word'")
    }

    private fun parseTerm(id: Id = freshId()): Term = when (peekChar()) {
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
                        Term.Def((left as? Term.Var)?.name ?: error("name expected"), arguments, id)
                    }
                    '@' -> {
                        skip()
                        val arguments = parseList('[', ']') { parseTerm() }
                        Term.Apply(left, arguments, id)
                    }
                    ':' -> {
                        skip()
                        val right = parseTerm()
                        Term.Anno(left, right, id)
                    }
                    '∈' -> {
                        skip()
                        val right = parseTerm()
                        Term.BoxOf(left, right, id)
                    }
                    '=' -> {
                        skip()
                        val right = parseTerm()
                        Term.Eq(left, right, id)
                    }
                    else -> {
                        val operator = parseTerm()
                        val right = parseTerm()
                        Term.Def((operator as? Term.Var)?.name ?: error("name expected"), listOf(left, right), id)
                    }
                }
            }.also { expect(')') }
        }
        '_' -> {
            skip()
            Term.Hole(id)
        }
        '?' -> {
            skip()
            Term.Meta(id)
        }
        '⟨' -> {
            skip()
            Term.UnitOf(id).also { expect('⟩') }
        }
        '"' -> Term.StringOf(readString(), id)
        '[' -> when {
            peek(1) == 'b' && peek(2) == ';' -> {
                skip(2)
                Term.ByteArrayOf(parseList(';', ']') { parseTerm() }, id)
            }
            peek(1) == 'i' && peek(2) == ';' -> {
                skip(2)
                Term.IntArrayOf(parseList(';', ']') { parseTerm() }, id)
            }
            peek(1) == 'l' && peek(2) == ';' -> {
                skip(2)
                Term.LongArrayOf(parseList(';', ']') { parseTerm() }, id)
            }
            else -> Term.ListOf(parseList('[', ']') { parseTerm() }, id)
        }
        '{' -> Term.CompoundOf(parseList('{', '}') { parsePair({ parseName() }, { expect(':') }, { parseTerm() }) }, id)
        '&' -> {
            skip()
            Term.RefOf(parseTerm(), id)
        }
        'λ' -> {
            skip()
            val parameters = parseList('[', ']') { parseName() }
            expectString("→")
            val body = parseTerm()
            Term.FunOf(parameters, body, id)
        }
        '`' -> {
            skip()
            Term.CodeOf(parseTerm(), id)
        }
        '$' -> {
            skip()
            Term.Splice(parseTerm(), id)
        }
        else -> when (val word = readWord()) {
            "let" -> {
                val name = readWord()
                expectString("≔")
                val init = parseTerm()
                expect(',')
                val body = parseTerm()
                Term.Let(name, init, body, id)
            }
            "match" -> {
                val scrutinee = parseTerm()
                val clauses = parseList('[', ']') { parsePair({ parsePattern() }, { expectString("⇒") }, { parseTerm() }) }
                Term.Match(scrutinee, clauses, id)
            }
            "false" -> Term.BoolOf(false, id)
            "true" -> Term.BoolOf(true, id)
            "refl" -> Term.Refl(id)
            "or" -> {
                val variants = parseList('{', '}') { parseTerm() }
                Term.Or(variants, id)
            }
            "end" -> Term.Or(emptyList(), id)
            "and" -> {
                val variants = parseList('{', '}') { parseTerm() }
                Term.And(variants, id)
            }
            "any" -> Term.And(emptyList(), id)
            "unit" -> Term.Unit(id)
            "bool" -> Term.Bool(id)
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
            "list" -> {
                val element = parseTerm()
                val size = parseTerm()
                Term.List(element, size, id)
            }
            "compound" -> {
                val elements = parseList('{', '}') {
                    val erased = if (peekChar() == '0') {
                        skip()
                        false
                    } else true
                    val left = parseTerm()
                    if (peekChar() == ',' || peekChar() == '}') {
                        Entry(erased, Name("", freshId()), left, freshId())
                    } else {
                        expect(':')
                        val right = parseTerm()
                        Entry(erased, Name((left as? Term.Var)?.name ?: error("name expected"), freshId()), right, freshId())
                    }
                }
                Term.Compound(elements, id)
            }
            "box" -> {
                val content = parseTerm()
                Term.Box(content, id)
            }
            "ref" -> Term.Ref(parseTerm(), id)
            "fun" -> {
                val parameters = parseList('[', ']') { parseParameter() }
                expectString("→")
                val resultant = parseTerm()
                val effects = if (peekChar() == '!') {
                    skip()
                    parseEffects()
                } else emptyList()
                Term.Fun(parameters, resultant, effects, id)
            }
            "code" -> Term.Code(parseTerm(), id)
            "type" -> Term.Type(id)
            else -> when {
                BYTE_EXPRESSION.matches(word) -> Term.ByteOf(word.dropLast(1).toByte(), id)
                SHORT_EXPRESSION.matches(word) -> Term.ShortOf(word.dropLast(1).toShort(), id)
                INT_EXPRESSION.matches(word) -> Term.IntOf(word.toInt(), id)
                LONG_EXPRESSION.matches(word) -> Term.LongOf(word.dropLast(1).toLong(), id)
                FLOAT_EXPRESSION.matches(word) -> Term.FloatOf(word.dropLast(1).toFloat(), id)
                DOUBLE_EXPRESSION.matches(word) -> Term.DoubleOf(word.dropLast(1).toDouble(), id)
                else -> Term.Var(word, id)
            }
        }
    }

    private fun parsePattern(id: Id = freshId()): Pattern = when (peekChar()) {
        '(' -> {
            skip()
            val left = parsePattern()
            when (val char = peekChar()) {
                ')' -> left
                '∈' -> {
                    skip()
                    val right = parsePattern()
                    Pattern.BoxOf(left, right, id)
                }
                else -> error("unexpected operator '$char'")
            }.also { expect(')') }
        }
        '⟨' -> {
            skip()
            Pattern.UnitOf(id).also { expect('⟩') }
        }
        '"' -> Pattern.StringOf(readString(), id)
        '[' -> when {
            peek(1) == 'b' && peek(2) == ';' -> {
                skip(2)
                Pattern.ByteArrayOf(parseList(';', ']') { parsePattern() }, id)
            }
            peek(1) == 'i' && peek(2) == ';' -> {
                skip(2)
                Pattern.IntArrayOf(parseList(';', ']') { parsePattern() }, id)
            }
            peek(1) == 'l' && peek(2) == ';' -> {
                skip(2)
                Pattern.LongArrayOf(parseList(';', ']') { parsePattern() }, id)
            }
            else -> Pattern.ListOf(parseList('[', ']') { parsePattern() }, id)
        }
        '{' -> Pattern.CompoundOf(parseList('{', '}') { parsePair({ parseName() }, { expect(':') }, { parsePattern() }) }, id)
        '&' -> {
            skip()
            Pattern.RefOf(parsePattern(), id)
        }
        else -> when (val word = readWord()) {
            "false" -> Pattern.BoolOf(false, id)
            "true" -> Pattern.BoolOf(true, id)
            "refl" -> Pattern.Refl(id)
            "unit" -> Pattern.Unit(id)
            "bool" -> Pattern.Bool(id)
            "byte" -> Pattern.Byte(id)
            "short" -> Pattern.Short(id)
            "int" -> Pattern.Int(id)
            "long" -> Pattern.Long(id)
            "float" -> Pattern.Float(id)
            "double" -> Pattern.Double(id)
            "string" -> Pattern.String(id)
            "byte_array" -> Pattern.ByteArray(id)
            "int_array" -> Pattern.IntArray(id)
            "long_array" -> Pattern.LongArray(id)
            else -> when {
                BYTE_EXPRESSION.matches(word) -> Pattern.ByteOf(word.dropLast(1).toByte(), id)
                SHORT_EXPRESSION.matches(word) -> Pattern.ShortOf(word.dropLast(1).toShort(), id)
                INT_EXPRESSION.matches(word) -> Pattern.IntOf(word.toInt(), id)
                LONG_EXPRESSION.matches(word) -> Pattern.LongOf(word.dropLast(1).toLong(), id)
                FLOAT_EXPRESSION.matches(word) -> Pattern.FloatOf(word.dropLast(1).toFloat(), id)
                DOUBLE_EXPRESSION.matches(word) -> Pattern.DoubleOf(word.dropLast(1).toDouble(), id)
                else -> Pattern.Var(word, id)
            }
        }
    }

    private fun parseEffects(): List<Effect> = parseList('{', '}') { parseEffect() }

    private fun parseEffect(): Effect {
        val name = readWord()
        return Effect.Name(name)
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

        operator fun invoke(name: String, input: String): Item = Parse(input).run {
            parseItem().also {
                if (it.name != name) error("")
                skipWhitespace()
                if (canRead()) error("end of file expected")
            }
        }
    }
}
