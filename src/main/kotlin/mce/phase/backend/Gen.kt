package mce.phase.backend

import mce.ast.pack.*
import java.io.Closeable
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import mce.ast.pack.Function as PFunction

class Gen(
    name: String,
) : Closeable {
    private val output: ZipOutputStream = ZipOutputStream(File("$name.zip").outputStream().buffered()) // TODO: file name

    private fun genFunction(function: PFunction) {
        output.putNextEntry(function.name.toEntry())
        function.commands.forEach { genCommand(it) }
        output.closeEntry()
    }

    private fun genCommand(command: Command) {
        when (command) {
            is Command.Execute -> TODO()
            is Command.CheckScore -> TODO()
            is Command.CheckMatchingData -> TODO()
            is Command.GetData -> TODO()
            is Command.GetNumeric -> TODO()
            is Command.RemoveData -> TODO()
            is Command.InsertAtIndex -> TODO()
            is Command.Prepend -> TODO()
            is Command.Append -> TODO()
            is Command.SetData -> TODO()
            is Command.MergeData -> TODO()
            is Command.RunFunction -> TODO()
            is Command.SetScore -> TODO()
            is Command.GetScore -> TODO()
            is Command.AddScore -> TODO()
            is Command.RemoveScore -> TODO()
            is Command.ResetScores -> TODO()
            is Command.ResetScore -> TODO()
            is Command.PerformOperation -> TODO()
        }
    }

    private fun genExecute(execute: Execute) {
        when (execute) {
            is Execute.Run -> TODO()
            is Execute.CheckScore -> TODO()
            is Execute.CheckMatchingData -> TODO()
            is Execute.StoreValue -> TODO()
            is Execute.StoreData -> TODO()
        }
    }

    private fun genStoreType(type: StoreType) {
        when (type) {
            StoreType.BYTE -> TODO()
            StoreType.SHORT -> TODO()
            StoreType.INT -> TODO()
            StoreType.LONG -> TODO()
            StoreType.FLOAT -> TODO()
            StoreType.DOUBLE -> TODO()
        }
    }

    private fun genSourceProvider(provider: SourceProvider) {
        when (provider) {
            is SourceProvider.Value -> TODO()
            is SourceProvider.From -> TODO()
        }
    }

    private fun genNbtPath(path: NbtPath) {
        TODO()
    }

    private fun genNbtNode(node: NbtNode) {
        when (node) {
            is NbtNode.MatchRootObject -> TODO()
            is NbtNode.MatchElement -> TODO()
            is NbtNode.AllElements -> TODO()
            is NbtNode.IndexedElement -> TODO()
            is NbtNode.MatchObject -> TODO()
            is NbtNode.CompoundChild -> TODO()
        }
    }

    private fun genNbt(nbt: Nbt) {
        when (nbt) {
            is Nbt.Byte -> TODO()
            is Nbt.Short -> TODO()
            is Nbt.Int -> TODO()
            is Nbt.Long -> TODO()
            is Nbt.Float -> TODO()
            is Nbt.Double -> TODO()
            is Nbt.ByteArray -> TODO()
            is Nbt.String -> TODO()
            is Nbt.List -> TODO()
            is Nbt.Compound -> TODO()
            is Nbt.IntArray -> TODO()
            is Nbt.LongArray -> TODO()
        }
    }

    private fun genObjective(objective: Objective) {
        TODO()
    }

    private fun genScoreHolder(holder: ScoreHolder) {
        TODO()
    }

    private fun genOperation(operation: Operation) {
        when (operation) {
            Operation.ASSIGN -> TODO()
            Operation.PLUS_ASSIGN -> TODO()
            Operation.MINUS_ASSIGN -> TODO()
            Operation.TIMES_ASSIGN -> TODO()
            Operation.DIV_ASSIGN -> TODO()
            Operation.MOD_ASSIGN -> TODO()
            Operation.MIN_ASSIGN -> TODO()
            Operation.MAX_ASSIGN -> TODO()
            Operation.SWAP -> TODO()
        }
    }

    private fun genSourceComparator(comparator: SourceComparator) {
        when (comparator) {
            is SourceComparator.Eq -> TODO()
            is SourceComparator.Lt -> TODO()
            is SourceComparator.Le -> TODO()
            is SourceComparator.Gt -> TODO()
            is SourceComparator.Ge -> TODO()
            is SourceComparator.Matches -> TODO()
        }
    }

    private fun genConsumer(consumer: Consumer) {
        when (consumer) {
            Consumer.RESULT -> TODO()
            Consumer.SUCCESS -> TODO()
        }
    }

    private fun genResourceLocation(location: ResourceLocation) {
        TODO()
    }

    private fun ResourceLocation.toEntry(): ZipEntry = ZipEntry("data/${namespace}/functions/${path}.mcfunction")

    override fun close() {
        output.close()
    }

    companion object {
        operator fun invoke(name: String, functions: List<PFunction>): Unit = Gen(name).use { gen ->
            functions.forEach { gen.genFunction(it) }
        }
    }
}
