package mce.emulator

import mce.ast.Packed.Command
import mce.ast.Packed.Consumer
import mce.ast.Packed.Execute
import mce.ast.Packed.Function
import mce.ast.Packed.NbtPath
import mce.ast.Packed.Objective
import mce.ast.Packed.ResourceLocation
import mce.ast.Packed.ScoreHolder
import mce.ast.Packed.SourceComparator
import mce.ast.Packed.SourceProvider
import mce.ast.Packed.StoreType
import mce.emulator.NbtLens.countMatching
import mce.emulator.NbtLens.get
import mce.emulator.NbtLens.remove
import mce.emulator.NbtLens.set

class Executor(
    private val storage: NbtStorage = NbtStorage(),
    private val scoreboard: Scoreboard = Scoreboard(),
) {
    private val queue: ArrayDeque<Command> = ArrayDeque()

    fun runFunction(function: Function) {
        TODO()
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

    private fun insertAtIndex(target: ResourceLocation, path: NbtPath, index: Int, source: SourceProvider): Int {
        TODO()
    }

    private fun setData(target: ResourceLocation, path: NbtPath, source: SourceProvider): Int {
        TODO()
    }

    private fun mergeData(target: ResourceLocation, path: NbtPath, source: SourceProvider): Int {
        TODO()
    }

    private fun runFunction(name: ResourceLocation): Int {
        TODO()
    }
}
