package dev.stepanov.gitrootsmanager.model

/** Объединяет корни из маппингов и собственного состояния для диалога. */
object RootsModel {

    fun unite(
        mappings: List<Mapping>,
        disabled: List<DisabledRoot>,
        detected: List<Mapping> = emptyList(),
        projectBasePath: String? = null,
    ): List<ManagedRoot> {
        val roots = linkedMapOf<String, ManagedRoot>()
        val hasDefault = mappings.any { it.directory.isEmpty() }
        val covered = if (hasDefault) {
            detected.mapTo(HashSet()) { MappingLogic.normalizeDirectory(it.directory) }
        } else {
            emptySet()
        }
        for (mapping in mappings) {
            val directory = MappingLogic.normalizeDirectory(mapping.directory)
            // при дефолт-маппинге корень проекта показывается строкой <Project>
            if (hasDefault && MappingLogic.isProjectRoot(directory, projectBasePath)) continue
            roots[directory] = ManagedRoot(directory, mapping.vcs, tracked = true)
        }
        for (root in disabled) {
            val directory = MappingLogic.normalizeDirectory(root.directory)
            if (hasDefault && MappingLogic.isProjectRoot(directory, projectBasePath)) continue
            roots.putIfAbsent(directory, ManagedRoot(directory, root.vcs, tracked = directory in covered))
        }
        for (root in detected) {
            val directory = MappingLogic.normalizeDirectory(root.directory)
            if (hasDefault && MappingLogic.isProjectRoot(directory, projectBasePath)) continue
            roots.putIfAbsent(directory, ManagedRoot(directory, root.vcs, tracked = hasDefault))
        }
        return roots.values.toList()
    }

    /** Разворачивает `<Project>` в явные маппинги, исключая [exclude]. */
    fun expandDefaultMapping(
        mappings: List<Mapping>,
        detected: List<Mapping>,
        exclude: Set<String>,
    ): List<Mapping> {
        val excluded = exclude.mapTo(HashSet()) { MappingLogic.normalizeDirectory(it) }
        val explicit = mappings
            .filter {
                val dir = MappingLogic.normalizeDirectory(it.directory)
                dir.isNotEmpty() && dir !in excluded
            }
            .associateBy { MappingLogic.normalizeDirectory(it.directory) }
        val result = explicit.values.toMutableList()
        for (root in detected) {
            val dir = MappingLogic.normalizeDirectory(root.directory)
            if (dir.isEmpty() || dir in excluded || dir in explicit) continue
            result.add(Mapping(dir, root.vcs))
        }
        return result
    }
}
