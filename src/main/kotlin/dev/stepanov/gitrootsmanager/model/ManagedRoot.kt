package dev.stepanov.gitrootsmanager.model

/**
 * Строка диалога: корень и его текущее состояние отслеживания.
 */
data class ManagedRoot(val directory: String, val vcs: String, val tracked: Boolean)
