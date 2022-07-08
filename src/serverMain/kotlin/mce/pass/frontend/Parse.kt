package mce.pass.frontend

import mce.Id
import mce.ast.Modifier
import mce.ast.Name
import mce.ast.surface.*
import mce.pass.freshId
import java.util.*

@Deprecated("Use [mce.serialization.MceDecoder] instead")
class Parse private constructor(
    private val source: String
) {
    private var cursor: Int = 0

    private fun parseItem(id: Id = freshId()): Item {
        val modifiers = if (peekChar() == '{') parseList('{', '}') { parseModifier() } else emptyList()
        var word = readWord()
        val imports = if (word == "import") parseList('{', '}') { readWord() }.also { word = readWord() } else emptyList()
        return when (word) {
            "def" -> {
                val name = readWord()
                val params = parseList('[', ']') { parseParam() }
                val withs = if (peekChar() == 'w') {
                    expectString("with")
                    parseList('[', ']') { parseParam() }
                } else emptyList()
                val resultant = run {
                    expect(':')
                    parseTerm()
                }
                val effects = if (peekChar() == '!') {
                    skip()
                    parseList('{', '}') { parseEff() }
                } else emptyList()
                val body = run {
                    expectString("≔")
                    parseTerm()
                }
                Item.Def(imports, modifiers, name, params, withs, resultant, effects, body, id)
            }
            "mod" -> {
                val name = readWord()
                expect(':')
                val type = parseModule()
                expectString("≔")
                val body = parseModule()
                Item.Mod(imports, modifiers, name, type, body, id)
            }
            "test" -> {
                val name = readWord()
                val body = run {
                    expectString("≔")
                    parseTerm()
                }
                Item.Test(imports, modifiers, name, body, id)
            }
            "pack" -> {
                val body = parseTerm()
                Item.Pack(body, id)
            }
            "advancement" -> {
                val name = readWord()
                val body = run {
                    expectString("≔")
                    parseTerm()
                }
                Item.Advancement(imports, modifiers, name, body, id)
            }
            else -> error("unexpected item '$word'")
        }
    }

    private fun parseModifier(): Modifier = when (val word = readWord()) {
        "abstract" -> Modifier.ABSTRACT
        "builtin" -> Modifier.BUILTIN
        "dynamic" -> Modifier.DYNAMIC
        "static" -> Modifier.STATIC
        "recursive" -> Modifier.RECURSIVE
        else -> error("unexpected modifier '$word'")
    }

    private fun parseParam(id: Id = freshId()): Param {
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
            Param(false, name, lower, upper, typeRelevant, type, id)
        } else {
            val term = parseTerm()
            return if (peekChar() == ',' || peekChar() == ']') {
                Param(true, "", null, null, true, term, id)
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
                Param(true, (term as Term.Var).name, lower, upper, typeRelevant, type, id)
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
            val parameters = parseList('[', ']') { parseParam() }
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
                val uuid = UUID.fromString(readWord())
                parseTerm(Id(uuid.mostSignificantBits, uuid.leastSignificantBits))
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
        '/' -> {
            skip()
            val body = parseTerm()
            Term.Command(body, id)
        }
        '⋆' -> {
            skip()
            Term.UnitOf(id)
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
        '{' -> {
            val elements = parseList('{', '}') {
                val name = parseName()
                expect(':')
                val element = parseTerm()
                Term.CompoundOf.Entry(name, element)
            }
            Term.CompoundOf(elements, id)
        }
        '⟨' -> {
            val elements = parseList('⟨', '⟩') { parseTerm() }
            Term.TupleOf(elements, id)
        }
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
            "block" -> {
                val elements = parseList('[', ']') { parseTerm() }
                Term.Block(elements, id)
            }
            "let" -> {
                val name = readWord()
                expectString("≔")
                val init = parseTerm()
                Term.Let(name, init, id)
            }
            "match" -> {
                val scrutinee = parseTerm()
                val clauses = parseList('[', ']') { parsePair({ parsePat() }, { expectString("⇒") }, { parseTerm() }) }
                Term.Match(scrutinee, clauses, id)
            }
            "false" -> Term.BoolOf(false, id)
            "true" -> Term.BoolOf(true, id)
            "refl" -> Term.Refl(id)
            "singleton" -> {
                val element = parseTerm()
                Term.Singleton(element, id)
            }
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
                        Term.Compound.Entry(erased, Name("", freshId()), left, freshId())
                    } else {
                        expect(':')
                        val right = parseTerm()
                        Term.Compound.Entry(erased, Name((left as? Term.Var)?.name ?: error("name expected"), freshId()), right, freshId())
                    }
                }
                Term.Compound(elements, id)
            }
            "tuple" -> {
                val elements = parseList('⟨', '⟩') {
                    val erased = if (peekChar() == '0') {
                        skip()
                        false
                    } else true
                    val left = parseTerm()
                    if (peekChar() == ',' || peekChar() == '⟩') {
                        Term.Tuple.Entry(erased, Name("", freshId()), left, freshId())
                    } else {
                        expect(':')
                        val right = parseTerm()
                        Term.Tuple.Entry(erased, Name((left as? Term.Var)?.name ?: error("name expected"), freshId()), right, freshId())
                    }
                }
                Term.Tuple(elements, id)
            }
            "ref" -> {
                val element = parseTerm()
                Term.Ref(element, id)
            }
            "fun" -> {
                val parameters = parseList('[', ']') { parseParam() }
                expectString("→")
                val resultant = parseTerm()
                val effects = if (peekChar() == '!') {
                    skip()
                    parseList('{', '}') { parseEff() }
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

    private fun parsePat(id: Id = freshId()): Pat = when (peekChar()) {
        '(' -> {
            skip()
            val left = parsePat()
            when (val char = peekChar()) {
                ')' -> left
                else -> error("unexpected operator '$char'")
            }.also { expect(')') }
        }
        '⋆' -> {
            skip()
            Pat.UnitOf(id)
        }
        '"' -> Pat.StringOf(readString(), id)
        '[' -> when {
            peek(1) == 'b' && peek(2) == ';' -> {
                skip(2)
                Pat.ByteArrayOf(parseList(';', ']') { parsePat() }, id)
            }
            peek(1) == 'i' && peek(2) == ';' -> {
                skip(2)
                Pat.IntArrayOf(parseList(';', ']') { parsePat() }, id)
            }
            peek(1) == 'l' && peek(2) == ';' -> {
                skip(2)
                Pat.LongArrayOf(parseList(';', ']') { parsePat() }, id)
            }
            else -> Pat.ListOf(parseList('[', ']') { parsePat() }, id)
        }
        '{' -> Pat.CompoundOf(parseList('{', '}') { parsePair({ parseName() }, { expect(':') }, { parsePat() }) }, id)
        '⟨' -> {
            val elements = parseList('⟨', '⟩') { parsePat() }
            Pat.TupleOf(elements, id)
        }
        '&' -> {
            skip()
            Pat.RefOf(parsePat(), id)
        }
        else -> when (val word = readWord()) {
            "false" -> Pat.BoolOf(false, id)
            "true" -> Pat.BoolOf(true, id)
            "refl" -> Pat.Refl(id)
            else -> when {
                BYTE_EXPRESSION.matches(word) -> Pat.ByteOf(word.dropLast(1).toByte(), id)
                SHORT_EXPRESSION.matches(word) -> Pat.ShortOf(word.dropLast(1).toShort(), id)
                INT_EXPRESSION.matches(word) -> Pat.IntOf(word.toInt(), id)
                LONG_EXPRESSION.matches(word) -> Pat.LongOf(word.dropLast(1).toLong(), id)
                FLOAT_EXPRESSION.matches(word) -> Pat.FloatOf(word.dropLast(1).toFloat(), id)
                DOUBLE_EXPRESSION.matches(word) -> Pat.DoubleOf(word.dropLast(1).toDouble(), id)
                else -> Pat.Var(word, id)
            }
        }
    }

    private fun parseEff(): Eff {
        val name = readWord()
        return Eff.Name(name)
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
            skipSpace()
            when (peek()) {
                ',', '\n' -> skip()
                else -> break
            }
        }
        expect(end)
    }

    private fun canRead(length: Int = 1): Boolean = cursor + length <= source.length

    private fun peek(offset: Int = 0): Char = source[cursor + offset]

    private fun skip(size: Int = 1) {
        cursor += size
    }

    private fun skipSpace() {
        while (canRead() && Character.isSpaceChar(peek())) skip()
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

    private fun Char.isWordPart(): Boolean = this != ',' && this != ')' && this != ']' && this != '}' && this != '⟩' && this != ':' && !this.isWhitespace()

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
