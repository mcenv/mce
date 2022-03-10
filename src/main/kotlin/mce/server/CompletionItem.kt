package mce.server

import mce.ast.Surface as S

data class CompletionItem(
    val name: String,
    val type: S.Term,
)
