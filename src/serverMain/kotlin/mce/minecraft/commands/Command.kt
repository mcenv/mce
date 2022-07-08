package mce.minecraft.commands

import mce.minecraft.ResourceLocation
import java.io.OutputStream

sealed class Command {
    fun gen(output: OutputStream): Unit = TODO()

    sealed class Advancement : Command() {
        data class Perform(val action: Action, val targets: String, val mode: Mode) : Advancement()

        enum class Action { GRANT, REVOKE, }

        sealed class Mode {
            data class Only(val advancement: ResourceLocation, val criterion: String? = null) : Mode()
            data class Through(val advancement: ResourceLocation) : Mode()
            data class From(val advancement: ResourceLocation) : Mode()
            data class Until(val advancement: ResourceLocation) : Mode()
            object Everything : Mode()
        }
    }

    sealed class Function : Command() {
        data class RunFunction(val name: ResourceLocation) : Function()
    }
}
