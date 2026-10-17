package dev.stepanov.gitrootsmanager.model

/** Объединяет корни из маппингов и собственного состояния для диалога. */
object RootsModel {

    fun withoutProjectRoot(roots: List<DisabledRoot>, projectBasePath: String?): List<DisabledRoot> {
        return roots.filterNot { MappingLogic.isProjectRoot(it.directory, projectBasePath) }
    }

    fun unite(mappings: List<Mapping>, disabled: List<DisabledRoot>): List<ManagedRoot> {
        val trackedDirs = mappings.mapTo(HashSet()) { MappingLogic.normalizeDirectory(it.directory) }
        val tracked = mappings.map { ManagedRoot(it.directory, it.vcs, tracked = true) }
        val untracked = disabled
            .filter { MappingLogic.normalizeDirectory(it.directory) !in trackedDirs }
            .map { ManagedRoot(it.directory, it.vcs, tracked = false) }
        return tracked + untracked
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
