package mce.emulator

import mce.ast.Packed.Command
import mce.ast.Packed.Execute
import mce.ast.Packed.Function
import mce.ast.Packed.NbtPath
import mce.ast.Packed.Objective
import mce.ast.Packed.ResourceLocation
import mce.ast.Packed.ScoreHolder
import mce.ast.Packed.SourceComparator
import mce.ast.Packed.SourceProvider
import mce.emulator.NbtLens.countMatching
import mce.emulator.NbtLens.get
import mce.emulator.NbtLens.remove

class Executor {
    private val queue: ArrayDeque<Command> = ArrayDeque()
    private val storage: NbtStorage = NbtStorage()

    fun runFunction(function: Function) {
        TODO()
    }

    private fun runCommand(command: Command): Int = when (command) {
        is Command.Execute -> execute(command.execute)
        is Command.CheckScore -> checkScore(command.success, command.target, command.targetObjective, command.source)
        is Command.CheckMatchingData -> checkMatchingData(command.success, command.source, command.path)
        is Command.GetData -> getData(command.target, command.path)
        is Command.GetNumeric -> getNumeric(command.target, command.path, command.scale)
        is Command.RemoveData -> removeData(command.target, command.path)
        is Command.InsertAtIndex -> insertAtIndex(command.target, command.path, command.index, command.source)
        is Command.SetData -> setData(command.target, command.path, command.source)
        is Command.MergeData -> mergeData(command.target, command.path, command.source)
        is Command.RunFunction -> runFunction(command.name)
    }

    private fun execute(execute: Execute): Int = when (execute) {
        is Execute.Run -> runCommand(execute.command)
        is Execute.CheckScore -> TODO()
        is Execute.CheckMatchingData -> TODO()
        is Execute.StoreValue -> TODO()
        is Execute.StoreData -> TODO()
    }

    private fun checkScore(success: Boolean, target: ScoreHolder, targetObjective: Objective, source: SourceComparator): Int {
        TODO()
    }

    private fun checkMatchingData(success: Boolean, source: ResourceLocation, path: NbtPath): Int {
        val matching = path.countMatching(storage[source])
        if (matching > 0 != success) {
            throw Exception()
        }
        return matching
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
