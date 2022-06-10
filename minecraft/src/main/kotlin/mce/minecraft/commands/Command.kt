package mce.minecraft.commands

import mce.minecraft.ResourceLocation

sealed class Command {
    sealed class Advancement : Command() {
        data class Perform(val action: Action, val targets: String, val mode: Mode) : Advancement()

        enum class Action { GRANT, REVOKE, }

        sealed class Mode {
            data class Only(val advancement: String, val criterion: String? = null) : Mode()
            data class Through(val advancement: String) : Mode()
            data class From(val advancement: String) : Mode()
            data class Until(val advancement: String) : Mode()
            object Everything : Mode()
        }
    }

    sealed class Function : Command() {
        data class RunFunction(val name: ResourceLocation) : Function()
    }
}
