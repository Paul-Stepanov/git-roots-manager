package dev.stepanov.gitrootsmanager.startup

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * При открытии проекта ставит слушателя изменений маппингов и повторно
 * применяет список выключенных корней (платформа могла вернуть их
 * авто-детекцией).
 */
class StartupReapplier : ProjectActivity {

    override suspend fun execute(project: Project) {
        val guard = project.getService(DisabledRootsGuard::class.java)
        withContext(Dispatchers.EDT) {
            guard.reapplyDisabledRoots()
        }
    }
}
