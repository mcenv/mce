package mce.minecraft.commands

import java.io.OutputStream

data class CommandFunction(
    val commands: List<Command>,
) {
    fun gen(output: OutputStream) {
        commands.forEachIndexed { index, command ->
            if (index != 0) {
                output.write('\n'.code)
            }
            command.gen(output)
        }
    }
}
