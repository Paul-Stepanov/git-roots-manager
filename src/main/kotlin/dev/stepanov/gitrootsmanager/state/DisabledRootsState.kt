package dev.stepanov.gitrootsmanager.state

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.State as ComponentState
import com.intellij.openapi.project.Project
import dev.stepanov.gitrootsmanager.model.DisabledRoot
import dev.stepanov.gitrootsmanager.model.MappingLogic
import dev.stepanov.gitrootsmanager.model.RootsModel

/** Локальное состояние проекта: выключенные корни с нормализованными путями. */
@Service(Service.Level.PROJECT)
@ComponentState(name = "GitRootsManagerState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class DisabledRootsState(private val project: Project) : PersistentStateComponent<DisabledRootsState.State> {

    class State {
        var disabled: MutableList<DisabledRoot> = mutableListOf()
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        state.disabled = RootsModel.withoutProjectRoot(state.disabled, project.basePath).toMutableList()
        this.state = state
    }

    fun getDisabled(): List<DisabledRoot> = state.disabled.toList()

    fun setDisabled(roots: List<DisabledRoot>) {
        state.disabled = RootsModel.withoutProjectRoot(roots, project.basePath).toMutableList()
    }

    fun add(root: DisabledRoot) {
        if (!isProjectRoot(root.directory) && state.disabled.none { it.directory == root.directory }) {
            state.disabled.add(root)
        }
    }

    fun remove(directory: String) {
        state.disabled.removeAll { it.directory == directory }
    }

    fun find(directory: String): DisabledRoot? {
        return state.disabled.firstOrNull { it.directory == directory }
    }

    private fun isProjectRoot(directory: String): Boolean {
        return MappingLogic.isProjectRoot(directory, project.basePath)
    }
}
