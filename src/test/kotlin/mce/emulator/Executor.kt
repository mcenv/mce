package mce.emulator

import mce.ast.pack.*
import mce.ast.pack.Function
import mce.emulator.NbtLens.countMatching
import mce.emulator.NbtLens.get
import mce.emulator.NbtLens.getOrCreate
import mce.emulator.NbtLens.remove
import mce.emulator.NbtLens.set
import kotlin.math.max
import kotlin.math.min

class Executor(
    private val functions: Map<ResourceLocation, Function> = emptyMap(),
    private val scoreboard: Scoreboard = Scoreboard(),
    private val storage: NbtStorage = NbtStorage(),
) {
    private val queue: ArrayDeque<Command> = ArrayDeque()

    fun runTopFunction(name: ResourceLocation) {
        queue += functions[name]!!.commands
        try {
            while (queue.isNotEmpty()) {
                runCommand(queue.removeFirst())
            }
        } finally {
            queue.clear()
        }
    }

    private fun runCommand(command: Command): Int = when (command) {
        is Command.Execute -> runExecute(command.execute)
        is Command.CheckScore -> if (checkScore(command.success, command.target, command.targetObjective, command.source)) 1 else throw Exception()
        is Command.CheckMatchingData -> if (checkMatchingData(command.success, command.source, command.path)) 1 else throw Exception()
        is Command.GetData -> getData(command.target, command.path)
        is Command.GetNumeric -> getNumeric(command.target, command.path, command.scale)
        is Command.RemoveData -> removeData(command.target, command.path)
        is Command.InsertAtIndex -> insertAtIndex(command.target, command.path, command.index, command.source)
        is Command.SetData -> setData(command.target, command.path, command.source)
        is Command.MergeData -> mergeData(command.target, command.path, command.source)
        is Command.RunFunction -> runFunction(command.name)
        is Command.SetScore -> setScore(command.targets, command.objective, command.score)
        is Command.GetScore -> getScore(command.target, command.objective)
        is Command.AddScore -> addScore(command.targets, command.objective, command.score)
        is Command.RemoveScore -> removeScore(command.targets, command.objective, command.score)
        is Command.ResetScores -> resetScores(command.targets)
        is Command.ResetScore -> resetScore(command.targets, command.objective)
        is Command.PerformOperation -> performOperation(command.targets, command.targetObjective, command.operation, command.source, command.sourceObjective)
    }

    private fun runExecute(execute: Execute): Int = when (execute) {
        is Execute.Run -> runCommand(execute.command)
        is Execute.CheckScore -> if (checkScore(execute.success, execute.target, execute.targetObjective, execute.source)) runExecute(execute.command) else throw Exception()
        is Execute.CheckMatchingData -> if (checkMatchingData(execute.success, execute.source, execute.path)) runExecute(execute.command) else throw Exception()
        is Execute.StoreValue -> storeValue(execute.consumer, execute.targets, execute.objective, execute.command)
        is Execute.StoreData -> storeData(execute.consumer, execute.target, execute.path, execute.type, execute.scale, execute.command)
    }

    private inline fun checkScore(target: ScoreHolder, targetObjective: Objective, source: ScoreHolder, sourceObjective: Objective, comparator: (Int, Int) -> Boolean): Boolean {
        return comparator(scoreboard[target, targetObjective], scoreboard[source, sourceObjective])
    }

    private fun checkScore(success: Boolean, target: ScoreHolder, targetObjective: Objective, source: SourceComparator): Boolean {
        return when (source) {
            is SourceComparator.Eq -> checkScore(target, targetObjective, source.source, source.sourceObjective) { a, b -> a == b }
            is SourceComparator.Lt -> checkScore(target, targetObjective, source.source, source.sourceObjective) { a, b -> a < b }
            is SourceComparator.Le -> checkScore(target, targetObjective, source.source, source.sourceObjective) { a, b -> a <= b }
            is SourceComparator.Gt -> checkScore(target, targetObjective, source.source, source.sourceObjective) { a, b -> a > b }
            is SourceComparator.Ge -> checkScore(target, targetObjective, source.source, source.sourceObjective) { a, b -> a >= b }
            is SourceComparator.Matches -> scoreboard.hasScore(target, targetObjective) && scoreboard[target, targetObjective] in source.range
        } == success
    }

    private fun checkMatchingData(success: Boolean, source: ResourceLocation, path: NbtPath): Boolean {
        return path.countMatching(storage[source]) > 0 == success
    }

    private fun getResult(consumer: Consumer, command: Execute): Int {
        val result = try {
            runExecute(command)
        } catch (e: Exception) {
            0
        }
        return when (consumer) {
            Consumer.RESULT -> result
            Consumer.SUCCESS -> if (result == 0) 0 else 1
        }
    }

    private fun storeValue(consumer: Consumer, targets: ScoreHolder, objective: Objective, command: Execute): Int {
        val result = getResult(consumer, command)
        scoreboard[targets, objective] = result
        return result
    }

    private fun storeData(consumer: Consumer, target: ResourceLocation, path: NbtPath, type: StoreType, scale: Double, command: Execute): Int {
        val result = getResult(consumer, command)
        val source = lazy {
            when (type) {
                StoreType.BYTE -> ByteNbt((result.toDouble() * scale).toInt().toByte())
                StoreType.SHORT -> ShortNbt((result.toDouble() * scale).toInt().toShort())
                StoreType.INT -> IntNbt((result.toDouble() * scale).toInt())
                StoreType.LONG -> LongNbt((result.toDouble() * scale).toLong())
                StoreType.FLOAT -> FloatNbt((result.toDouble() * scale).toFloat())
                StoreType.DOUBLE -> DoubleNbt(result.toDouble() * scale)
            }
        }
        path.set(storage[target], source)
        return result
    }

    private fun getSingleNbt(target: ResourceLocation, path: NbtPath): MutableNbt {
        val nbts = path.get(storage[target])
        return when (nbts.size) {
            1 -> nbts.first()
            else -> throw Exception()
        }
    }

    private fun getData(target: ResourceLocation, path: NbtPath?): Int {
        return when (path) {
            null -> 1
            else -> when (val nbt = getSingleNbt(target, path)) {
                is NumericNbt -> floor(nbt.toDouble())
                is CollectionNbt<*> -> nbt.size
                is CompoundNbt -> nbt.size
                is StringNbt -> nbt.data.length
            }
        }
    }

    private fun getNumeric(target: ResourceLocation, path: NbtPath, scale: Double): Int {
        return when (val nbt = getSingleNbt(target, path)) {
            is NumericNbt -> floor(nbt.toDouble() * scale)
            else -> throw Exception()
        }
    }

    private fun removeData(target: ResourceLocation, path: NbtPath): Int {
        val nbt = storage[target]
        val modified = path.remove(nbt)
        when (modified) {
            0 -> throw Exception()
            else -> storage[target] = nbt
        }
        return modified
    }

    private fun getSources(source: SourceProvider): List<MutableNbt> {
        return when (source) {
            is SourceProvider.Value -> listOf(source.value.toMutableNbt())
            is SourceProvider.From -> when (val p = source.path) {
                null -> listOf(storage[source.source])
                else -> p.get(storage[source.source])
            }
        }
    }

    private fun insertAtIndex(target: ResourceLocation, path: NbtPath, index: Int, source: SourceProvider): Int {
        val targets = path.getOrCreate(storage[target], lazy { ListNbt(mutableListOf(), null) })
        val sources = getSources(source)
        var result = 0
        for (t in targets) {
            if (t !is CollectionNbt<*>) {
                throw Exception()
            }
            var r = 0
            var i = if (index < 0) t.size + index + 1 else index
            for (s in sources) {
                if (t.addNbt(i, s.clone())) {
                    ++i
                    r = 1
                }
            }
            result += r
        }
        return result
    }

    private fun setData(target: ResourceLocation, path: NbtPath, source: SourceProvider): Int {
        return path.set(storage[target], lazy { getSources(source).last() })
    }

    private fun mergeData(target: ResourceLocation, path: NbtPath, source: SourceProvider): Int {
        val targets = path.getOrCreate(storage[target], lazy { CompoundNbt(mutableMapOf()) })
        val sources = getSources(source)
        var result = 0
        for (t in targets) {
            if (t !is CompoundNbt) {
                throw Exception()
            }
            val before = t.clone()
            for (s in sources) {
                if (s !is CompoundNbt) {
                    throw Exception()
                }
                t.merge(s)
            }
            if (before != t) {
                ++result
            }
        }
        return result
    }

    private fun runFunction(name: ResourceLocation): Int {
        functions[name]!!.commands.asReversed().forEach {
            queue.addFirst(it)
        }
        return 0
    }

    // TODO: support wildcard
    private fun setScore(targets: ScoreHolder, objective: Objective, value: Int): Int {
        scoreboard[targets, objective] = value
        return value
    }

    private fun getScore(target: ScoreHolder, objective: Objective): Int {
        if (scoreboard.hasScore(target, objective)) {
            return scoreboard[target, objective]
        } else {
            throw Exception()
        }
    }

    private fun addScore(targets: ScoreHolder, objective: Objective, value: Int): Int {
        val result = scoreboard[targets, objective] + value
        scoreboard[targets, objective] = result
        return result
    }

    private fun removeScore(targets: ScoreHolder, objective: Objective, value: Int): Int {
        val result = scoreboard[targets, objective] - value
        scoreboard[targets, objective] = result
        return result
    }

    private fun resetScores(targets: ScoreHolder): Int {
        scoreboard.resetScores(targets)
        return 1
    }

    private fun resetScore(targets: ScoreHolder, objective: Objective): Int {
        scoreboard.resetScore(targets, objective)
        return 1
    }

    private fun performOperation(targets: ScoreHolder, targetObjective: Objective, operation: Operation, source: ScoreHolder, sourceObjective: Objective): Int {
        return when (operation) {
            Operation.SWAP -> {
                val result = scoreboard[source, sourceObjective]
                scoreboard[source, sourceObjective] = scoreboard[targets, targetObjective]
                scoreboard[targets, targetObjective] = result
                result
            }
            else -> {
                val apply = { a: Int, b: Int ->
                    when (operation) {
                        Operation.ASSIGN -> b
                        Operation.PLUS_ASSIGN -> a + b
                        Operation.MINUS_ASSIGN -> a - b
                        Operation.TIMES_ASSIGN -> a * b
                        Operation.DIV_ASSIGN -> Math.floorDiv(a, b)
                        Operation.MOD_ASSIGN -> Math.floorMod(a, b)
                        Operation.MIN_ASSIGN -> min(a, b)
                        Operation.MAX_ASSIGN -> max(a, b)
                        else -> throw Error()
                    }
                }
                val result = apply(scoreboard[targets, targetObjective], scoreboard[source, sourceObjective])
                scoreboard[targets, targetObjective] = result
                result
            }
        }
    }
}
