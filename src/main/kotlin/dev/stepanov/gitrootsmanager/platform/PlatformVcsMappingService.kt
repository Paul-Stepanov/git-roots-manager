package dev.stepanov.gitrootsmanager.platform

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.roots.VcsRootDetector
import dev.stepanov.gitrootsmanager.model.Mapping
import dev.stepanov.gitrootsmanager.model.MappingLogic

/**
 * Реализация [VcsMappingService] поверх API платформы 262
 * (см. docs/superpowers/plans/262-api-notes.md).
 */
@Service(Service.Level.PROJECT)
class PlatformVcsMappingService(private val project: Project) : VcsMappingService {

    override fun currentMappings(): List<Mapping> {
        return ProjectLevelVcsManager.getInstance(project)
            .getDirectoryMappings()
            .map { Mapping(MappingLogic.normalizeDirectory(it.directory), it.vcs) }
    }

    override fun applyMappings(mappings: List<Mapping>) {
        ProjectLevelVcsManager.getInstance(project).setDirectoryMappings(
            mappings.map { VcsDirectoryMapping(it.directory, it.vcs) },
        )
    }

    override fun ignoreRoot(directory: String) {
        VcsConfiguration.getInstance(project).addIgnoredUnregisteredRoots(listOf(directory))
    }

    override fun unignoreRoot(directory: String) {
        VcsConfiguration.getInstance(project).removeFromIgnoredUnregisteredRoots(listOf(directory))
    }

    override fun requestRescan() {
        // публичного API запуска VcsRootScanner в 262 нет (impl-модуль вне
        // compile classpath плагина); getOrDetect() выполняет обнаружение
        // по требованию. Обнаружение сканирует ФС — только не на EDT.
        ApplicationManager.getApplication().executeOnPooledThread {
            if (!project.isDisposed) {
                VcsRootDetector.getInstance(project).getOrDetect()
            }
        }
    }

    companion object {
        fun get(project: Project): VcsMappingService {
            return project.getService(PlatformVcsMappingService::class.java)
        }
    }
}
