package dev.stepanov.gitrootsmanager.state

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.State as ComponentState
import dev.stepanov.gitrootsmanager.model.DisabledRoot

/**
 * Локальное состояние проекта: корни, отключённые из отслеживания.
 * [DisabledRoot.directory] хранится нормализованным (обязанность вызывающего).
 */
@Service(Service.Level.PROJECT)
@ComponentState(name = "GitRootsManagerState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class DisabledRootsState : PersistentStateComponent<DisabledRootsState.State> {

    class State {
        var disabled: MutableList<DisabledRoot> = mutableListOf()
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun getDisabled(): List<DisabledRoot> = state.disabled.toList()

    fun setDisabled(roots: List<DisabledRoot>) {
        state.disabled = roots.toMutableList()
    }

    fun add(root: DisabledRoot) {
        if (state.disabled.none { it.directory == root.directory }) {
            state.disabled.add(root)
        }
    }

    fun remove(directory: String) {
        state.disabled.removeAll { it.directory == directory }
    }

    fun find(directory: String): DisabledRoot? {
        return state.disabled.firstOrNull { it.directory == directory }
    }
}
