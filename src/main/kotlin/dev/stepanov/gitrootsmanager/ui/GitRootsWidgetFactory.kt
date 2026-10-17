package dev.stepanov.gitrootsmanager.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import dev.stepanov.gitrootsmanager.core.GitRootsService

/**
 * Фабрика виджета статус-бара; виден только в проектах с отслеживаемыми
 * или ранее выключенными корнями.
 */
class GitRootsWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = WIDGET_ID

    override fun getDisplayName(): String = DISPLAY_NAME

    override fun isAvailable(project: Project): Boolean {
        if (project.isDisposed) return false
        return GitRootsService.get(project).listRoots().isNotEmpty()
    }

    override fun createWidget(project: Project): StatusBarWidget = GitRootsWidget(project)

    companion object {
        const val WIDGET_ID = "GitRootsManagerWidget"
        const val DISPLAY_NAME = "Git Roots Manager"
    }
}
