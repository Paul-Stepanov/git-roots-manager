package dev.stepanov.gitrootsmanager.model

/**
 * Выключенный корень: значение по умолчанию у всех параметров даёт
 * no-arg конструктор для XML-сериализации состояния плагина.
 */
data class DisabledRoot(val directory: String = "", val vcs: String = "")
