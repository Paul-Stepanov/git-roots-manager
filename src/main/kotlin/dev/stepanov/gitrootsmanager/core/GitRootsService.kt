package dev.stepanov.gitrootsmanager.core

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import dev.stepanov.gitrootsmanager.model.DisabledRoot
import dev.stepanov.gitrootsmanager.model.ManagedRoot
import dev.stepanov.gitrootsmanager.model.MappingLogic
import dev.stepanov.gitrootsmanager.model.RootsModel
import dev.stepanov.gitrootsmanager.platform.PlatformVcsMappingService
import dev.stepanov.gitrootsmanager.state.DisabledRootsState
import dev.stepanov.gitrootsmanager.ui.GitRootsWidgetFactory

/**
 * Оркестрация списка корней и мгновенного включения/выключения отслеживания.
 * Вызывается на EDT (диалог, виджет, guard-службы).
 */
@Service(Service.Level.PROJECT)
class GitRootsService(private val project: Project) {

    fun listRoots(): List<ManagedRoot> {
        return RootsModel.unite(
            PlatformVcsMappingService.get(project).currentMappings(),
            project.getService(DisabledRootsState::class.java).getDisabled(),
        )
    }

    fun setTracked(root: ManagedRoot, tracked: Boolean) {
        if (root.directory.isEmpty()) return
        val vcs = PlatformVcsMappingService.get(project)
        val state = project.getService(DisabledRootsState::class.java)
        if (tracked) {
            val restoredVcs = state.find(root.directory)?.vcs ?: root.vcs
            state.remove(root.directory)
            vcs.applyMappings(MappingLogic.addMapping(vcs.currentMappings(), root.directory, restoredVcs))
            vcs.unignoreRoot(root.directory)
        } else {
            vcs.applyMappings(MappingLogic.removeMapping(vcs.currentMappings(), root.directory))
            state.add(DisabledRoot(root.directory, root.vcs))
            vcs.ignoreRoot(root.directory)
            vcs.requestRescan()
        }
        // 262 требует явного пересоздания виджета при смене isAvailable
        WindowManager.getInstance().getStatusBar(project)
            ?.updateWidget(GitRootsWidgetFactory.WIDGET_ID)
    }

    companion object {
        fun get(project: Project): GitRootsService {
            return project.getService(GitRootsService::class.java)
        }
    }
}
