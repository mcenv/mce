package mce.phase.backend.gen

import mce.phase.backend.gen.Gen.ByteArrayCache.write
import mce.phase.backend.pack.*
import java.io.Closeable
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import mce.phase.backend.pack.Function as PFunction

class Gen(
    name: String,
) : Closeable {
    private val output: ZipOutputStream = ZipOutputStream(File("$name.zip").outputStream().buffered()) // use abstract type for testing

    private fun genFunction(function: PFunction) {
        output.putNextEntry(function.name.toEntry())
        function.commands.forEachIndexed { index, command ->
            if (index != 0) {
                output.write('\n')
            }
            output.genCommand(command)
        }
        output.closeEntry()
    }

    private fun ZipOutputStream.genCommand(command: Command) {
        when (command) {
            is Command.Execute -> {
                write("execute ")
                genExecute(command.execute)
            }
            is Command.CheckScore -> { // TODO: omit 'execute' when possible
                if (command.success) {
                    write("execute if score ")
                } else {
                    write("execute unless score ")
                }
                genScoreHolder(command.target)
                write(' ')
                genObjective(command.targetObjective)
                write(' ')
                genSourceComparator(command.source)
            }
            is Command.CheckMatchingData -> { // TODO: omit 'execute' when possible
                if (command.success) {
                    write("execute if data storage ")
                } else {
                    write("execute unless data storage ")
                }
                genResourceLocation(command.source)
                write(' ')
                genNbtPath(command.path)
            }
            is Command.GetData -> {
                write("data get storage ")
                genResourceLocation(command.target)
                command.path?.let {
                    write(' ')
                    genNbtPath(it)
                }
            }
            is Command.GetNumeric -> {
                write("data get storage ")
                genResourceLocation(command.target)
                write(' ')
                genNbtPath(command.path)
                write(' ')
                write(command.scale.toString()) // TODO: optimize representation for faster parsing and lower footprint
            }
            is Command.RemoveData -> {
                write("data remove storage ")
                genResourceLocation(command.target)
                write(' ')
                genNbtPath(command.path)
            }
            is Command.InsertAtIndex -> {
                write("data modify storage ")
                genResourceLocation(command.target)
                write(' ')
                genNbtPath(command.path)
                write(" insert ")
                write(command.index.toString())
                write(' ')
                genSourceProvider(command.source)
            }
            is Command.Prepend -> {
                write("data modify storage ")
                genResourceLocation(command.target)
                write(' ')
                genNbtPath(command.path)
                write(" prepend ")
                genSourceProvider(command.source)
            }
            is Command.Append -> {
                write("data modify storage ")
                genResourceLocation(command.target)
                write(' ')
                genNbtPath(command.path)
                write(" append ")
                genSourceProvider(command.source)
            }
            is Command.SetData -> {
                write("data modify storage ")
                genResourceLocation(command.target)
                write(' ')
                genNbtPath(command.path)
                write(" set ")
                genSourceProvider(command.source)
            }
            is Command.MergeData -> { // TODO: root merge
                write("data modify storage ")
                genResourceLocation(command.target)
                write(' ')
                genNbtPath(command.path)
                write(" merge ")
                genSourceProvider(command.source)
            }
            is Command.RunFunction -> {
                write("function ")
                genResourceLocation(command.name)
            }
            is Command.SetScore -> {
                write("scoreboard players set ")
                genScoreHolder(command.targets)
                write(' ')
                genObjective(command.objective)
                write(' ')
                write(command.score.toString())
            }
            is Command.GetScore -> {
                write("scoreboard players get ")
                genScoreHolder(command.target)
                write(' ')
                genObjective(command.objective)
            }
            is Command.AddScore -> {
                write("scoreboard players add ")
                genScoreHolder(command.targets)
                write(' ')
                genObjective(command.objective)
                write(' ')
                write(command.score.toString())
            }
            is Command.RemoveScore -> {
                write("scoreboard players remove ")
                genScoreHolder(command.targets)
                write(' ')
                genObjective(command.objective)
                write(' ')
                write(command.score.toString())
            }
            is Command.ResetScores -> {
                write("scoreboard players reset ")
                genScoreHolder(command.targets)
            }
            is Command.ResetScore -> {
                write("scoreboard players reset ")
                genScoreHolder(command.targets)
                write(' ')
                genObjective(command.objective)
            }
            is Command.PerformOperation -> {
                write("scoreboard players operation ")
                genScoreHolder(command.targets)
                write(' ')
                genObjective(command.targetObjective)
                write(' ')
                genOperation(command.operation)
                write(' ')
                genScoreHolder(command.source)
                write(' ')
                genObjective(command.sourceObjective)
            }
        }
    }

    private fun ZipOutputStream.genExecute(execute: Execute) {
        when (execute) {
            is Execute.Run -> {
                write("run ")
                genCommand(execute.command)
            }
            is Execute.CheckScore -> {
                if (execute.success) {
                    write("if score ")
                } else {
                    write("unless score ")
                }
                genScoreHolder(execute.target)
                write(' ')
                genObjective(execute.targetObjective)
                write(' ')
                genSourceComparator(execute.source)
                write(' ')
                genExecute(execute.execute)
            }
            is Execute.CheckMatchingData -> {
                if (execute.success) {
                    write("if data storage ")
                } else {
                    write("unless data storage ")
                }
                genResourceLocation(execute.source)
                write(' ')
                genNbtPath(execute.path)
                write(' ')
                genExecute(execute.execute)
            }
            is Execute.StoreValue -> {
                write("store ")
                genConsumer(execute.consumer)
                write(' ')
                genScoreHolder(execute.targets)
                write(' ')
                genObjective(execute.objective)
                write(' ')
                genExecute(execute.execute)
            }
            is Execute.StoreData -> {
                write("store ")
                genConsumer(execute.consumer)
                write(' ')
                genResourceLocation(execute.target)
                write(' ')
                genNbtPath(execute.path)
                write(' ')
                genStoreType(execute.type)
                write(' ')
                write(execute.scale.toString()) // TODO: optimize representation for faster parsing and lower footprint
                write(' ')
                genExecute(execute.execute)
            }
        }
    }

    private fun ZipOutputStream.genStoreType(type: StoreType) {
        when (type) {
            StoreType.BYTE -> write("byte")
            StoreType.SHORT -> write("short")
            StoreType.INT -> write("int")
            StoreType.LONG -> write("long")
            StoreType.FLOAT -> write("float")
            StoreType.DOUBLE -> write("double")
        }
    }

    private fun ZipOutputStream.genSourceProvider(provider: SourceProvider) {
        when (provider) {
            is SourceProvider.Value -> {
                write("value ")
                genNbt(provider.value)
            }
            is SourceProvider.From -> {
                write("from storage ")
                genResourceLocation(provider.source)
                provider.path?.let {
                    write(' ')
                    genNbtPath(it)
                }
            }
        }
    }

    private fun ZipOutputStream.genNbtPath(path: NbtPath) {
        genNbtNode(path.nodes.first())
        path.nodes.drop(1).forEach {
            when (it) {
                is NbtNode.MatchElement, is NbtNode.AllElements -> Unit
                else -> write('.')
            }
            genNbtNode(it)
        }
    }

    private fun ZipOutputStream.genNbtNode(node: NbtNode) {
        when (node) {
            is NbtNode.MatchRootObject -> genNbt(node.pattern)
            is NbtNode.MatchElement -> {
                write('[')
                genNbt(node.pattern)
                write(']')
            }
            is NbtNode.AllElements -> write("[]")
            is NbtNode.IndexedElement -> {
                write('[')
                write(node.index.toString())
                write(']')
            }
            is NbtNode.MatchObject -> {
                write(node.name)
                genNbt(node.pattern)
            }
            is NbtNode.CompoundChild -> write(node.name)
        }
    }

    private fun ZipOutputStream.genNbt(nbt: Nbt) {
        when (nbt) {
            is Nbt.Byte -> {
                write(nbt.data.toString())
                write('b')
            }
            is Nbt.Short -> {
                write(nbt.data.toString())
                write('s')
            }
            is Nbt.Int -> write(nbt.data.toString())
            is Nbt.Long -> {
                write(nbt.data.toString())
                write('l')
            }
            is Nbt.Float -> {
                write(nbt.data.toString()) // TODO: optimize representation for faster parsing and lower footprint
                write('f')
            }
            is Nbt.Double -> {
                write(nbt.data.toString()) // TODO: optimize representation for faster parsing and lower footprint
                write('d') // TODO: omit 'd' depending on the configuration
            }
            is Nbt.ByteArray -> {
                write("[B;")
                nbt.elements.forEachIndexed { index, element ->
                    if (index != 0) {
                        write(',')
                    }
                    write(element.toString())
                    write('b')
                }
                write(']')
            }
            is Nbt.String -> genQuotedString(nbt.data)
            is Nbt.List -> {
                write('[')
                nbt.elements.forEachIndexed { index, element ->
                    if (index != 0) {
                        write(',')
                    }
                    genNbt(element)
                }
                write(']')
            }
            is Nbt.Compound -> {
                write('{')
                nbt.elements.entries.forEachIndexed { index, (key, element) ->
                    if (index != 0) {
                        write(',')
                    }
                    genQuotedString(key)
                    write(':')
                    genNbt(element)
                }
                write('}')
            }
            is Nbt.IntArray -> {
                write("[I;")
                nbt.elements.forEachIndexed { index, element ->
                    if (index != 0) {
                        write(',')
                    }
                    write(element.toString())
                }
                write(']')
            }
            is Nbt.LongArray -> {
                write("[L;")
                nbt.elements.forEachIndexed { index, element ->
                    if (index != 0) {
                        write(',')
                    }
                    write(element.toString())
                    write('l')
                }
                write(']')
            }
        }
    }

    private fun ZipOutputStream.genObjective(objective: Objective) {
        write(objective.name)
    }

    private fun ZipOutputStream.genScoreHolder(holder: ScoreHolder) {
        write(holder.name)
    }

    private fun ZipOutputStream.genOperation(operation: Operation) {
        when (operation) {
            Operation.ASSIGN -> write('=')
            Operation.PLUS_ASSIGN -> write("+=")
            Operation.MINUS_ASSIGN -> write("-=")
            Operation.TIMES_ASSIGN -> write("*=")
            Operation.DIV_ASSIGN -> write("/=")
            Operation.MOD_ASSIGN -> write("%=")
            Operation.MIN_ASSIGN -> write('<')
            Operation.MAX_ASSIGN -> write('>')
            Operation.SWAP -> write("><")
        }
    }

    private fun ZipOutputStream.genSourceComparator(comparator: SourceComparator) {
        when (comparator) {
            is SourceComparator.EqScore -> {
                write("= ")
                genScoreHolder(comparator.source)
                write(' ')
                genObjective(comparator.sourceObjective)
            }
            is SourceComparator.LtScore -> {
                write("< ")
                genScoreHolder(comparator.source)
                write(' ')
                genObjective(comparator.sourceObjective)
            }
            is SourceComparator.LeScore -> {
                write("<= ")
                genScoreHolder(comparator.source)
                write(' ')
                genObjective(comparator.sourceObjective)
            }
            is SourceComparator.GtScore -> {
                write("> ")
                genScoreHolder(comparator.source)
                write(' ')
                genObjective(comparator.sourceObjective)
            }
            is SourceComparator.GeScore -> {
                write(">= ")
                genScoreHolder(comparator.source)
                write(' ')
                genObjective(comparator.sourceObjective)
            }
            is SourceComparator.EqConst -> {
                write("matches ")
                write(comparator.value.toString())
                write("..")
                write(comparator.value.toString())
            }
            is SourceComparator.LeConst -> {
                write("matches ..")
                write(comparator.value.toString())
            }
            is SourceComparator.GeConst -> {
                write("matches ")
                write(comparator.value.toString())
                write("..")
            }
        }
    }

    private fun ZipOutputStream.genConsumer(consumer: Consumer) {
        when (consumer) {
            Consumer.RESULT -> write("result")
            Consumer.SUCCESS -> write("success")
        }
    }

    private fun ZipOutputStream.genResourceLocation(location: ResourceLocation) {
        write(location.namespace)
        write(':')
        write(location.path)
    }

    private fun ZipOutputStream.genQuotedString(string: String) {
        write('"')
        write(string) // TODO: handle escape sequences
        write('"')
    }

    private fun ResourceLocation.toEntry(): ZipEntry = ZipEntry("data/${namespace}/functions/${path}.mcfunction")

    private fun ZipOutputStream.write(char: Char): Unit = write(char.code)

    override fun close() {
        output.close()
    }

    private object ByteArrayCache {
        private val arrays: MutableMap<String, ByteArray> = mutableMapOf()

        fun ZipOutputStream.write(string: String): Unit = write(arrays.computeIfAbsent(string) { string.toByteArray() })
    }

    companion object {
        operator fun invoke(name: String, functions: List<PFunction>): Unit = Gen(name).use { gen ->
            functions.forEach { gen.genFunction(it) }
        }
    }
}
