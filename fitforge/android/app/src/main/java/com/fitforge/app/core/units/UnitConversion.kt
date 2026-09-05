package com.fitforge.app.core.units

private const val KG_PER_LB = 0.45359237

/** Storage is always metric (CLAUDE.md); these only affect display. */
fun kgToLb(kg: Double): Double = kg / KG_PER_LB

fun lbToKg(lb: Double): Double = lb * KG_PER_LB
