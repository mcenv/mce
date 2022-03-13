package mce.emulator

import mce.ast.Packed.Objective
import mce.ast.Packed.ScoreHolder

class Scoreboard(
    private val objectives: MutableMap<String, Objective> = mutableMapOf(),
    private val scores: MutableMap<ScoreHolder, MutableMap<Objective, Int>> = mutableMapOf(),
) {
    fun hasScore(holder: ScoreHolder, objective: Objective): Boolean =
        scores[holder]?.containsKey(objective) ?: false

    operator fun get(holder: ScoreHolder, objective: Objective): Int =
        scores.computeIfAbsent(holder) { mutableMapOf() }.computeIfAbsent(objective) { 0 }

    operator fun set(holder: ScoreHolder, objective: Objective, score: Int) {
        this[holder, objective]
        scores[holder]!![objective] = score
    }

    fun resetScores(holder: ScoreHolder) {
        scores -= holder
    }

    fun resetScore(holder: ScoreHolder, objective: Objective) {
        scores[holder]?.let {
            it -= objective
            if (it.isEmpty()) {
                resetScores(holder)
            }
        }
    }
}
