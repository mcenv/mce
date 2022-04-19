package mce.pass.backend

import mce.ast.pack.*
import mce.pass.Config
import mce.pass.Pass
import mce.ast.pack.Function as PFunction

class Gen(
    private val generator: Generator,
) {
    private fun genFunction(function: PFunction) {
        generator.entry(function.name) {
            function.commands.forEachIndexed { index, command ->
                if (index != 0) {
                    generator.write('\n')
                }
                genCommand(command)
            }
        }
    }

    private fun genCommand(command: Command) {
        when (command) {
            is Command.Execute -> {
                generator.write("execute ")
                genExecute(command.execute)
            }
            is Command.CheckScore -> { // TODO: omit 'execute' when possible
                if (command.success) {
                    generator.write("execute if score ")
                } else {
                    generator.write("execute unless score ")
                }
                genScoreHolder(command.target)
                generator.write(' ')
                genObjective(command.targetObjective)
                generator.write(' ')
                genSourceComparator(command.source)
            }
            is Command.CheckMatchingData -> { // TODO: omit 'execute' when possible
                if (command.success) {
                    generator.write("execute if data storage ")
                } else {
                    generator.write("execute unless data storage ")
                }
                genResourceLocation(command.source)
                generator.write(' ')
                genNbtPath(command.path)
            }
            is Command.GetData -> {
                generator.write("data get storage ")
                genResourceLocation(command.target)
                command.path?.let {
                    generator.write(' ')
                    genNbtPath(it)
                }
            }
            is Command.GetNumeric -> {
                generator.write("data get storage ")
                genResourceLocation(command.target)
                generator.write(' ')
                genNbtPath(command.path)
                generator.write(' ')
                generator.write(command.scale.toString()) // TODO: optimize representation for faster parsing and lower footprint
            }
            is Command.RemoveData -> {
                generator.write("data remove storage ")
                genResourceLocation(command.target)
                generator.write(' ')
                genNbtPath(command.path)
            }
            is Command.InsertAtIndex -> {
                generator.write("data modify storage ")
                genResourceLocation(command.target)
                generator.write(' ')
                genNbtPath(command.path)
                generator.write(" insert ")
                generator.write(command.index.toString())
                generator.write(' ')
                genSourceProvider(command.source)
            }
            is Command.Prepend -> {
                generator.write("data modify storage ")
                genResourceLocation(command.target)
                generator.write(' ')
                genNbtPath(command.path)
                generator.write(" prepend ")
                genSourceProvider(command.source)
            }
            is Command.Append -> {
                generator.write("data modify storage ")
                genResourceLocation(command.target)
                generator.write(' ')
                genNbtPath(command.path)
                generator.write(" append ")
                genSourceProvider(command.source)
            }
            is Command.SetData -> {
                generator.write("data modify storage ")
                genResourceLocation(command.target)
                generator.write(' ')
                genNbtPath(command.path)
                generator.write(" set ")
                genSourceProvider(command.source)
            }
            is Command.MergeData -> { // TODO: root merge
                generator.write("data modify storage ")
                genResourceLocation(command.target)
                generator.write(' ')
                genNbtPath(command.path)
                generator.write(" merge ")
                genSourceProvider(command.source)
            }
            is Command.RunFunction -> {
                generator.write("function ")
                genResourceLocation(command.name)
            }
            is Command.SetScore -> {
                generator.write("scoreboard players set ")
                genScoreHolder(command.targets)
                generator.write(' ')
                genObjective(command.objective)
                generator.write(' ')
                generator.write(command.score.toString())
            }
            is Command.GetScore -> {
                generator.write("scoreboard players get ")
                genScoreHolder(command.target)
                generator.write(' ')
                genObjective(command.objective)
            }
            is Command.AddScore -> {
                generator.write("scoreboard players add ")
                genScoreHolder(command.targets)
                generator.write(' ')
                genObjective(command.objective)
                generator.write(' ')
                generator.write(command.score.toString())
            }
            is Command.RemoveScore -> {
                generator.write("scoreboard players remove ")
                genScoreHolder(command.targets)
                generator.write(' ')
                genObjective(command.objective)
                generator.write(' ')
                generator.write(command.score.toString())
            }
            is Command.ResetScores -> {
                generator.write("scoreboard players reset ")
                genScoreHolder(command.targets)
            }
            is Command.ResetScore -> {
                generator.write("scoreboard players reset ")
                genScoreHolder(command.targets)
                generator.write(' ')
                genObjective(command.objective)
            }
            is Command.PerformOperation -> {
                generator.write("scoreboard players operation ")
                genScoreHolder(command.targets)
                generator.write(' ')
                genObjective(command.targetObjective)
                generator.write(' ')
                genOperation(command.operation)
                generator.write(' ')
                genScoreHolder(command.source)
                generator.write(' ')
                genObjective(command.sourceObjective)
            }
        }
    }

    private fun genExecute(execute: Execute) {
        when (execute) {
            is Execute.Run -> {
                generator.write("run ")
                genCommand(execute.command)
            }
            is Execute.CheckScore -> {
                if (execute.success) {
                    generator.write("if score ")
                } else {
                    generator.write("unless score ")
                }
                genScoreHolder(execute.target)
                generator.write(' ')
                genObjective(execute.targetObjective)
                generator.write(' ')
                genSourceComparator(execute.source)
                generator.write(' ')
                genExecute(execute.execute)
            }
            is Execute.CheckMatchingData -> {
                if (execute.success) {
                    generator.write("if data storage ")
                } else {
                    generator.write("unless data storage ")
                }
                genResourceLocation(execute.source)
                generator.write(' ')
                genNbtPath(execute.path)
                generator.write(' ')
                genExecute(execute.execute)
            }
            is Execute.StoreValue -> {
                generator.write("store ")
                genConsumer(execute.consumer)
                generator.write(" score ")
                genScoreHolder(execute.targets)
                generator.write(' ')
                genObjective(execute.objective)
                generator.write(' ')
                genExecute(execute.execute)
            }
            is Execute.StoreData -> {
                generator.write("store ")
                genConsumer(execute.consumer)
                generator.write(" storage ")
                genResourceLocation(execute.target)
                generator.write(' ')
                genNbtPath(execute.path)
                generator.write(' ')
                genStoreType(execute.type)
                generator.write(' ')
                generator.write(execute.scale.toString()) // TODO: optimize representation for faster parsing and lower footprint
                generator.write(' ')
                genExecute(execute.execute)
            }
        }
    }

    private fun genStoreType(type: StoreType) {
        when (type) {
            StoreType.BYTE -> generator.write("byte")
            StoreType.SHORT -> generator.write("short")
            StoreType.INT -> generator.write("int")
            StoreType.LONG -> generator.write("long")
            StoreType.FLOAT -> generator.write("float")
            StoreType.DOUBLE -> generator.write("double")
        }
    }

    private fun genSourceProvider(provider: SourceProvider) {
        when (provider) {
            is SourceProvider.Value -> {
                generator.write("value ")
                genNbt(provider.value)
            }
            is SourceProvider.From -> {
                generator.write("from storage ")
                genResourceLocation(provider.source)
                provider.path?.let {
                    generator.write(' ')
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
                else -> generator.write('.')
            }
            genNbtNode(it)
        }
    }

    private fun genNbtNode(node: NbtNode) {
        when (node) {
            is NbtNode.MatchRootObject -> genNbt(node.pattern)
            is NbtNode.MatchElement -> {
                generator.write('[')
                genNbt(node.pattern)
                generator.write(']')
            }
            is NbtNode.AllElements -> generator.write("[]")
            is NbtNode.IndexedElement -> {
                generator.write('[')
                generator.write(node.index.toString())
                generator.write(']')
            }
            is NbtNode.MatchObject -> {
                generator.write(node.name)
                genNbt(node.pattern)
            }
            is NbtNode.CompoundChild -> generator.write(node.name)
        }
    }

    private fun genNbt(nbt: Nbt) {
        when (nbt) {
            is Nbt.Byte -> {
                generator.write(nbt.data.toString())
                generator.write('b')
            }
            is Nbt.Short -> {
                generator.write(nbt.data.toString())
                generator.write('s')
            }
            is Nbt.Int -> generator.write(nbt.data.toString())
            is Nbt.Long -> {
                generator.write(nbt.data.toString())
                generator.write('l')
            }
            is Nbt.Float -> {
                generator.write(nbt.data.toString()) // TODO: optimize representation for faster parsing and lower footprint
                generator.write('f')
            }
            is Nbt.Double -> {
                generator.write(nbt.data.toString()) // TODO: optimize representation for faster parsing and lower footprint
                generator.write('d') // TODO: omit 'd' depending on the configuration
            }
            is Nbt.ByteArray -> {
                generator.write("[B;")
                nbt.elements.forEachIndexed { index, element ->
                    if (index != 0) {
                        generator.write(',')
                    }
                    generator.write(element.toString())
                    generator.write('b')
                }
                generator.write(']')
            }
            is Nbt.String -> genQuotedString(nbt.data)
            is Nbt.List -> {
                generator.write('[')
                nbt.elements.forEachIndexed { index, element ->
                    if (index != 0) {
                        generator.write(',')
                    }
                    genNbt(element)
                }
                generator.write(']')
            }
            is Nbt.Compound -> {
                generator.write('{')
                nbt.elements.entries.forEachIndexed { index, (key, element) ->
                    if (index != 0) {
                        generator.write(',')
                    }
                    genQuotedString(key)
                    generator.write(':')
                    genNbt(element)
                }
                generator.write('}')
            }
            is Nbt.IntArray -> {
                generator.write("[I;")
                nbt.elements.forEachIndexed { index, element ->
                    if (index != 0) {
                        generator.write(',')
                    }
                    generator.write(element.toString())
                }
                generator.write(']')
            }
            is Nbt.LongArray -> {
                generator.write("[L;")
                nbt.elements.forEachIndexed { index, element ->
                    if (index != 0) {
                        generator.write(',')
                    }
                    generator.write(element.toString())
                    generator.write('l')
                }
                generator.write(']')
            }
        }
    }

    private fun genObjective(objective: Objective) {
        generator.write(objective.name)
    }

    private fun genScoreHolder(holder: ScoreHolder) {
        generator.write(holder.name)
    }

    private fun genOperation(operation: Operation) {
        when (operation) {
            Operation.ASSIGN -> generator.write('=')
            Operation.PLUS_ASSIGN -> generator.write("+=")
            Operation.MINUS_ASSIGN -> generator.write("-=")
            Operation.TIMES_ASSIGN -> generator.write("*=")
            Operation.DIV_ASSIGN -> generator.write("/=")
            Operation.MOD_ASSIGN -> generator.write("%=")
            Operation.MIN_ASSIGN -> generator.write('<')
            Operation.MAX_ASSIGN -> generator.write('>')
            Operation.SWAP -> generator.write("><")
        }
    }

    private fun genSourceComparator(comparator: SourceComparator) {
        when (comparator) {
            is SourceComparator.EqScore -> {
                generator.write("= ")
                genScoreHolder(comparator.source)
                generator.write(' ')
                genObjective(comparator.sourceObjective)
            }
            is SourceComparator.LtScore -> {
                generator.write("< ")
                genScoreHolder(comparator.source)
                generator.write(' ')
                genObjective(comparator.sourceObjective)
            }
            is SourceComparator.LeScore -> {
                generator.write("<= ")
                genScoreHolder(comparator.source)
                generator.write(' ')
                genObjective(comparator.sourceObjective)
            }
            is SourceComparator.GtScore -> {
                generator.write("> ")
                genScoreHolder(comparator.source)
                generator.write(' ')
                genObjective(comparator.sourceObjective)
            }
            is SourceComparator.GeScore -> {
                generator.write(">= ")
                genScoreHolder(comparator.source)
                generator.write(' ')
                genObjective(comparator.sourceObjective)
            }
            is SourceComparator.EqConst -> {
                generator.write("matches ")
                generator.write(comparator.value.toString())
                generator.write("..")
                generator.write(comparator.value.toString())
            }
            is SourceComparator.LeConst -> {
                generator.write("matches ..")
                generator.write(comparator.value.toString())
            }
            is SourceComparator.GeConst -> {
                generator.write("matches ")
                generator.write(comparator.value.toString())
                generator.write("..")
            }
        }
    }

    private fun genConsumer(consumer: Consumer) {
        when (consumer) {
            Consumer.RESULT -> generator.write("result")
            Consumer.SUCCESS -> generator.write("success")
        }
    }

    private fun genResourceLocation(location: ResourceLocation) {
        if (location.namespace != ResourceLocation.DEFAULT) {
            generator.write(location.namespace)
            generator.write(':')
        }
        generator.write(location.path)
    }

    private fun genQuotedString(string: String) {
        generator.write('"')
        generator.write(string) // TODO: handle escape sequences
        generator.write('"')
    }

    data class Result(
        val generate: (Generator) -> Unit,
    )

    companion object : Pass<Pack.Result, Result> {
        override operator fun invoke(config: Config, input: Pack.Result): Result = Result { generator ->
            val gen = Gen(generator)
            input.functions.forEach { gen.genFunction(it) }
            // TODO: gen defunctions
        }
    }
}

interface Generator {
    fun entry(name: ResourceLocation, block: Generator.() -> Unit)

    fun write(char: Char)

    fun write(string: String)
}
