package dev.stepanov.gitrootsmanager.startup

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.stepanov.gitrootsmanager.core.GitRootsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * При открытии проекта ставит список выключенных корней на место и прогревает
 * детекцию, чтобы виджет появился даже когда маппингов нет совсем.
 */
class StartupReapplier : ProjectActivity {

    override suspend fun execute(project: Project) {
        val guard = project.getService(DisabledRootsGuard::class.java)
        withContext(Dispatchers.EDT) {
            guard.reapplyDisabledRoots()
            GitRootsService.get(project).listRootsAsync(ModalityState.any()) {
                GitRootsService.get(project).refreshWidget()
            }
        }
    }
}
