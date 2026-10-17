package dev.stepanov.gitrootsmanager.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.IconManager
import com.intellij.util.Consumer
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * Иконка в статус-баре; клик открывает диалог управления корнями.
 */
class GitRootsWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.IconPresentation {

    override fun ID(): String = GitRootsWidgetFactory.WIDGET_ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getTooltipText(): String = GitRootsWidgetFactory.DISPLAY_NAME

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        if (!project.isDisposed) {
            GitRootsDialog(project).show()
        }
    }

    override fun getIcon(): Icon = ICON

    override fun dispose() {}

    companion object {
        private val ICON = IconManager.getInstance()
            .getIcon("/icons/gitRootsManager16.svg", GitRootsWidget::class.java)
    }
}
