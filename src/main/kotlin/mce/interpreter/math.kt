package mce.interpreter

fun floor(a: Float): Int = a.toInt().let { if (a < it.toFloat()) it - 1 else it }

fun floor(a: Double): Int = a.toInt().let { if (a < it.toDouble()) it - 1 else it }
