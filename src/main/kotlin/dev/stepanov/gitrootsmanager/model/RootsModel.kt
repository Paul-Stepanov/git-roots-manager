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
}
