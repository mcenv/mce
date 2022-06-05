package mce.pass.backend

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import mce.ast.pack.*
import mce.ast.pack.Command.GetData
import mce.ast.pack.Command.RunFunction
import mce.ast.pack.Consumer.RESULT
import mce.ast.pack.Execute.Run
import mce.ast.pack.Execute.StoreValue
import mce.ast.pack.SourceComparator.EqConst
import mce.minecraft.ResourceLocation
import mce.minecraft.chat.LiteralComponent
import mce.minecraft.packs.PackMetadata
import mce.minecraft.packs.PackMetadataSection
import mce.minecraft.tags.Tag
import mce.pass.Config
import mce.pass.Pass
import mce.util.DATA_PACK_FORMAT
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import mce.ast.pack.Command.Execute as E
import mce.ast.pack.Function as PFunction

@ExperimentalSerializationApi
class Gen {
    private val output: ZipOutputStream = ZipOutputStream(File("out.zip").outputStream().buffered())
    private val arrays: MutableMap<String, ByteArray> = mutableMapOf()

    init {
        output.putNextEntry(ZipEntry("pack.mcmeta"))
        Json.encodeToStream(PackMetadata(PackMetadataSection(LiteralComponent(""), DATA_PACK_FORMAT)), output)
        output.closeEntry()
    }

    private fun genTag(path: String, name: ResourceLocation, tag: Tag) {
        entry(ResourceType.Tags(path), name) {
            Json.encodeToStream(tag, output)
        }
    }

    private fun genAdvancement(name: ResourceLocation, advancement: Advancement) {
        entry(ResourceType.Advancements, name) {
            Json.encodeToStream(advancement, output)
        }
    }

    private fun genFunction(name: ResourceLocation, function: PFunction) {
        entry(ResourceType.Functions, name) {
            function.commands.forEachIndexed { index, command ->
                if (index != 0) {
                    write('\n')
                }
                genCommand(command)
            }
        }
    }

    private fun genCommand(command: Command) {
        when (command) {
            is Command.Raw -> {
                write(command.body)
            }
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

    private fun genExecute(execute: Execute) {
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
                write(" score ")
                genScoreHolder(execute.targets)
                write(' ')
                genObjective(execute.objective)
                write(' ')
                genExecute(execute.execute)
            }
            is Execute.StoreData -> {
                write("store ")
                genConsumer(execute.consumer)
                write(" storage ")
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

    private fun genStoreType(type: StoreType) {
        when (type) {
            StoreType.BYTE -> write("byte")
            StoreType.SHORT -> write("short")
            StoreType.INT -> write("int")
            StoreType.LONG -> write("long")
            StoreType.FLOAT -> write("float")
            StoreType.DOUBLE -> write("double")
        }
    }

    private fun genSourceProvider(provider: SourceProvider) {
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

    private fun genNbtPath(path: NbtPath) {
        genNbtNode(path.nodes.first())
        path.nodes.drop(1).forEach {
            when (it) {
                is NbtNode.MatchElement, is NbtNode.AllElements, is NbtNode.IndexedElement -> Unit
                else -> write('.')
            }
            genNbtNode(it)
        }
    }

    private fun genNbtNode(node: NbtNode) {
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

    private fun genNbt(nbt: Nbt) {
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

    private fun genObjective(objective: Objective) {
        write(objective.name)
    }

    private fun genScoreHolder(holder: ScoreHolder) {
        write(holder.name)
    }

    private fun genOperation(operation: Operation) {
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

    private fun genSourceComparator(comparator: SourceComparator) {
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

    private fun genConsumer(consumer: Consumer) {
        when (consumer) {
            Consumer.RESULT -> write("result")
            Consumer.SUCCESS -> write("success")
        }
    }

    private fun genResourceLocation(location: ResourceLocation) {
        if (location.namespace != ResourceLocation.DEFAULT_NAMESPACE) {
            write(ResourceLocation.normalize(location.namespace))
            write(':')
        }
        write(ResourceLocation.normalize(location.path))
    }

    private fun genQuotedString(string: String) {
        write('"')
        write(string) // TODO: handle escape sequences
        write('"')
    }

    private inline fun entry(type: ResourceType, location: ResourceLocation, block: () -> Unit) {
        val namespace = if (location.namespace == ResourceLocation.DEFAULT_NAMESPACE) location.namespace else ResourceLocation.normalize(location.namespace)
        val path = when {
            type is ResourceType.Tags && type.path == "functions" && (location.path == "load" || location.path == "tick") -> location.path
            else -> ResourceLocation.normalize(location.path)
        }
        output.putNextEntry(ZipEntry("data/${namespace}/${type.directory}/${path}.${type.extension}"))
        block()
        output.closeEntry()
    }

    private fun write(char: Char) {
        output.write(char.code)
    }

    private fun write(string: String) {
        output.write(arrays.computeIfAbsent(string) { string.toByteArray() })
    }

    companion object : Pass<Pack.Result, Unit> {
        override operator fun invoke(config: Config, input: Pack.Result): Unit = Gen().run {
            input.tags.forEach { (key, tag) -> genTag(key.first, key.second, tag) }
            input.advancements.forEach { (name, advancement) -> genAdvancement(name, advancement) }
            input.functions.forEach { (name, function) -> genFunction(name, function) }

            genFunction(
                APPLY,
                PFunction(
                    listOf(
                        E(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, INT[-1])))),
                        Pop(MAIN, INT),
                    ) + input.defunctions.map { (tag, defunction) ->
                        val name = ResourceLocation("$tag")
                        genFunction(name, defunction)
                        E(Execute.CheckScore(true, R0, REG, EqConst(tag), Run(RunFunction(name))))
                    })
            )

            output.close()
        }
    }
}
