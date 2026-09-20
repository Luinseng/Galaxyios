package com.gearan.watch.ui.screens

/** Staged error shown on Watch and mirrored in Diagnostics. */
data class GearanStageError(
    val stage: String,
    val code: String,
    val message: String,
)
