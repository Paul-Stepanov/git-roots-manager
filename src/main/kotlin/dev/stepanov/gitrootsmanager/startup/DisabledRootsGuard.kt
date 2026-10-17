package dev.stepanov.gitrootsmanager.startup

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsMappingListener
import dev.stepanov.gitrootsmanager.model.MappingLogic
import dev.stepanov.gitrootsmanager.platform.PlatformVcsMappingService
import dev.stepanov.gitrootsmanager.state.DisabledRootsState

/**
 * Повторно убирает выключенные корни, вернувшиеся в маппинги; слушает
 * изменения directory mappings. Идемпотентно: при пустом пересечении
 * «выключенные ∩ текущие маппинги» записей нет — этим же гасится петля
 * от собственного изменения маппингов.
 */
@Service(Service.Level.PROJECT)
class DisabledRootsGuard(private val project: Project) : com.intellij.openapi.Disposable {

    init {
        project.messageBus.connect(this).subscribe(
            ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED,
            VcsMappingListener {
                // поток доставки события не гарантирован EDT, а маппинги требуют EDT
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        reapplyDisabledRoots()
                    }
                }
            },
        )
    }

    fun reapplyDisabledRoots() {
        val state = project.getService(DisabledRootsState::class.java)
        val disabled = state.getDisabled()
        if (disabled.isEmpty()) return
        val vcs = PlatformVcsMappingService.get(project)
        val current = vcs.currentMappings()
        val leaked = disabled.filter { root -> current.any { it.directory == root.directory } }
        if (leaked.isEmpty()) return
        var mappings = current
        for (root in leaked) {
            mappings = MappingLogic.removeMapping(mappings, root.directory)
        }
        vcs.applyMappings(mappings)
        LOG.info("Re-removed ${leaked.size} disabled root(s): ${leaked.map { it.directory }}")
    }

    override fun dispose() {}

    private companion object {
        val LOG = Logger.getInstance(DisabledRootsGuard::class.java)
    }
}
