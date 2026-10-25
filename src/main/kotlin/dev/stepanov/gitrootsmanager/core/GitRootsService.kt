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
 * Управляет корнями, включая корень проекта и `<Project>`-маппинг: выключение
 * запоминает вид маппинга, включение возвращает его. Детекция идёт вне EDT,
 * изменения маппингов — на EDT. Асинхронные методы вызывают колбэк на EDT.
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
        if (hasDefaultMapping() && root.directory.isNotEmpty()) {
            setTrackedInDefaultMode(root, tracked, modalityState, onDone)
            return
        }
        val vcs = PlatformVcsMappingService.get(project)
        val state = project.getService(DisabledRootsState::class.java)
        if (tracked) {
            val restoredVcs = state.find(root.directory)?.vcs ?: root.vcs
            state.remove(root.directory)
            vcs.applyMappings(MappingLogic.addMapping(vcs.currentMappings(), root.directory, restoredVcs))
            ignoredDirectory(root.directory)?.let { vcs.unignoreRoot(it) }
        } else {
            vcs.applyMappings(MappingLogic.removeMapping(vcs.currentMappings(), root.directory))
            state.add(DisabledRoot(root.directory, root.vcs))
            ignoredDirectory(root.directory)?.let { vcs.ignoreRoot(it) }
            vcs.requestRescan()
        }
        refreshWidget()
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
            .filter { it.tracked != tracked }
            .forEach { setTracked(it, tracked, modalityState) }
        onDone()
    }

    /** Дефолт-режим: включение снимает «выключено», выключение разворачивает `<Project>`. */
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
            refreshWidget()
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
            refreshWidget()
            onDone()
        }
    }

    /** Полное выключение снимает маппинги разом, не разворачивая дефолт в явные. */
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
            disabled.forEach { ignoredDirectory(it.directory)?.let(vcs::unignoreRoot) }
            refreshWidget()
            onDone()
            return
        }
        runDetached(modalityState) { detected ->
            val defaultVcs = vcs.currentMappings()
                .firstOrNull { it.directory.isEmpty() }?.vcs
            if (defaultVcs == null) {
                onDone()
                return@runDetached
            }
            vcs.applyMappings(emptyList())
            state.setDisabled(detected.map { DisabledRoot(it.directory, it.vcs) } + DisabledRoot("", defaultVcs))
            detected.forEach { vcs.ignoreRoot(it.directory) }
            ignoredDirectory("")?.let(vcs::ignoreRoot)
            vcs.requestRescan()
            refreshWidget()
            onDone()
        }
    }

    private fun hasDefaultMapping(): Boolean {
        return PlatformVcsMappingService.get(project).currentMappings().any { it.directory.isEmpty() }
    }

    /** `<Project>` кодируется пустой строкой, а от детектора прячется сам корень проекта. */
    private fun ignoredDirectory(directory: String): String? {
        return if (directory.isEmpty()) project.basePath else directory
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

    fun refreshWidget() {
        WindowManager.getInstance().getStatusBar(project)
            ?.updateWidget(GitRootsWidgetFactory.WIDGET_ID)
    }

    companion object {
        fun get(project: Project): GitRootsService {
            return project.getService(GitRootsService::class.java)
        }
    }
}
