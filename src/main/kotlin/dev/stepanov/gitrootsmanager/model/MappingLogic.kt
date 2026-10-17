package dev.stepanov.gitrootsmanager.model

/**
 * Чистые операции над списком маппингов;
 * сравнение путей выполняется только по нормализованному [Mapping.directory].
 */
object MappingLogic {

    fun normalizeDirectory(directory: String): String {
        val trimmed = directory.trim()
        if (trimmed.isEmpty() || trimmed == "/") return trimmed
        return trimmed.trimEnd('/')
    }

    fun removeMapping(mappings: List<Mapping>, directory: String): List<Mapping> {
        val target = normalizeDirectory(directory)
        return mappings.filter { normalizeDirectory(it.directory) != target }
    }

    fun addMapping(mappings: List<Mapping>, directory: String, vcs: String): List<Mapping> {
        val target = normalizeDirectory(directory)
        val replaced = mappings.any { normalizeDirectory(it.directory) == target }
        if (!replaced) return mappings + Mapping(target, vcs)
        return mappings.map {
            if (normalizeDirectory(it.directory) == target) it.copy(vcs = vcs) else it
        }
    }
}
