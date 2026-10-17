package dev.stepanov.gitrootsmanager.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import dev.stepanov.gitrootsmanager.model.DisabledRoot
import dev.stepanov.gitrootsmanager.model.ManagedRoot
import dev.stepanov.gitrootsmanager.model.Mapping
import dev.stepanov.gitrootsmanager.model.MappingLogic
import dev.stepanov.gitrootsmanager.model.RootsModel
import dev.stepanov.gitrootsmanager.platform.PlatformVcsMappingService
import dev.stepanov.gitrootsmanager.state.DisabledRootsState
import dev.stepanov.gitrootsmanager.ui.GitRootsWidgetFactory

/**
 * Управляет корнями, включая `<Project>`: первое выключение разворачивает
 * дефолт-маппинг. Детекция идёт вне EDT, изменения маппингов — на EDT.
 * Асинхронные методы вызывают колбэк на EDT.
 */
@Service(Service.Level.PROJECT)
class GitRootsService(private val project: Project) {

    private var detectedRoots: List<Mapping> = emptyList()

    /** Снимок на EDT; детекцию обновляет [listRootsAsync]. */
    fun listRoots(): List<ManagedRoot> {
        return RootsModel.unite(
            PlatformVcsMappingService.get(project).currentMappings(),
            project.getService(DisabledRootsState::class.java).getDisabled(),
            detectedRoots,
            project.basePath,
        )
    }

    fun listRootsAsync(modalityState: ModalityState, onDone: (List<ManagedRoot>) -> Unit) {
        runDetached(modalityState) { onDone(listRoots()) }
    }

    fun setTracked(
        root: ManagedRoot,
        tracked: Boolean,
        modalityState: ModalityState,
        onDone: () -> Unit = {},
    ) {
        if (root.directory.isEmpty() || MappingLogic.isProjectRoot(root.directory, project.basePath)) {
            onDone()
            return
        }
        if (hasDefaultMapping()) {
            setTrackedInDefaultMode(root, tracked, modalityState, onDone)
            return
        }
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
        updateWidgetAvailability()
        onDone()
    }

    fun setAllTracked(
        tracked: Boolean,
        knownRoots: List<ManagedRoot>,
        modalityState: ModalityState,
        onDone: () -> Unit = {},
    ) {
        if (hasDefaultMapping()) {
            setAllTrackedInDefaultMode(tracked, modalityState, onDone)
            return
        }
        knownRoots
            .filter { it.toggleable && it.tracked != tracked }
            .forEach { setTracked(it, tracked, modalityState) }
        onDone()
    }

    private fun setTrackedInDefaultMode(
        root: ManagedRoot,
        tracked: Boolean,
        modalityState: ModalityState,
        onDone: () -> Unit,
    ) {
        val state = project.getService(DisabledRootsState::class.java)
        val vcs = PlatformVcsMappingService.get(project)
        if (tracked) {
            state.remove(root.directory)
            vcs.unignoreRoot(root.directory)
            updateWidgetAvailability()
            onDone()
            return
        }
        runDetached(modalityState) { detected ->
            val expanded = RootsModel.expandDefaultMapping(
                vcs.currentMappings(), detected, setOf(root.directory),
            )
            vcs.applyMappings(expanded)
            state.add(DisabledRoot(root.directory, root.vcs))
            vcs.ignoreRoot(root.directory)
            vcs.requestRescan()
            updateWidgetAvailability()
            onDone()
        }
    }

    private fun setAllTrackedInDefaultMode(
        tracked: Boolean,
        modalityState: ModalityState,
        onDone: () -> Unit,
    ) {
        val state = project.getService(DisabledRootsState::class.java)
        val vcs = PlatformVcsMappingService.get(project)
        if (tracked) {
            val disabled = state.getDisabled()
            state.setDisabled(emptyList())
            disabled.forEach { vcs.unignoreRoot(it.directory) }
            updateWidgetAvailability()
            onDone()
            return
        }
        runDetached(modalityState) { detected ->
            val roots = listRoots().filter { it.toggleable }
            val excluded = roots.mapTo(HashSet()) { it.directory }
            vcs.applyMappings(RootsModel.expandDefaultMapping(vcs.currentMappings(), detected, excluded))
            state.setDisabled(roots.map { DisabledRoot(it.directory, it.vcs) })
            roots.forEach { vcs.ignoreRoot(it.directory) }
            vcs.requestRescan()
            updateWidgetAvailability()
            onDone()
        }
    }

    private fun hasDefaultMapping(): Boolean {
        return PlatformVcsMappingService.get(project).currentMappings().any { it.directory.isEmpty() }
    }

    private fun runDetached(modalityState: ModalityState, onDetected: (List<Mapping>) -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread(Runnable {
            val detected = if (project.isDisposed) {
                emptyList<Mapping>()
            } else {
                PlatformVcsMappingService.get(project).detectedRoots()
            }
            postToEdt(modalityState) {
                detectedRoots = detected
                onDetected(detected)
            }
        })
    }

    private fun postToEdt(modalityState: ModalityState, block: () -> Unit) {
        ApplicationManager.getApplication().invokeLater({
            if (!project.isDisposed) block()
        }, modalityState)
    }

    private fun updateWidgetAvailability() {
        WindowManager.getInstance().getStatusBar(project)
            ?.updateWidget(GitRootsWidgetFactory.WIDGET_ID)
    }

    companion object {
        fun get(project: Project): GitRootsService {
            return project.getService(GitRootsService::class.java)
        }
    }
}
