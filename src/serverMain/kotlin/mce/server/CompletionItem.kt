package mce.server

import mce.phase.frontend.decode.Term

data class CompletionItem(
    val name: String,
    val type: Term,
)
