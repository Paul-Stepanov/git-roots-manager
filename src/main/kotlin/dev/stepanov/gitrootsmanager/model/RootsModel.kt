package dev.stepanov.gitrootsmanager.model

/**
 * Объединяет включённые (маппинги платформы) и выключенные (состояние плагина)
 * корни в список для диалога.
 */
object RootsModel {

    fun unite(mappings: List<Mapping>, disabled: List<DisabledRoot>): List<ManagedRoot> {
        val trackedDirs = mappings.mapTo(HashSet()) { MappingLogic.normalizeDirectory(it.directory) }
        val tracked = mappings.map { ManagedRoot(it.directory, it.vcs, tracked = true) }
        val untracked = disabled
            .filter { MappingLogic.normalizeDirectory(it.directory) !in trackedDirs }
            .map { ManagedRoot(it.directory, it.vcs, tracked = false) }
        return tracked + untracked
    }

    /**
     * Разворачивает дефолт-маппинг `<Project>` (directory = "") в явные:
     * существующие явные маппинги сохраняются, из обнаруженных добавляются
     * недостающие, корни из [exclude] (выключаемые) не попадают в результат.
     */
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
