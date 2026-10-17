package dev.stepanov.gitrootsmanager.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.components.JBCheckBox
import dev.stepanov.gitrootsmanager.core.GitRootsService
import dev.stepanov.gitrootsmanager.model.ManagedRoot
import dev.stepanov.gitrootsmanager.platform.PlatformVcsMappingService
import java.awt.GridLayout
import java.io.File
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Список git-корней с мгновенным применением по клику чекбокса;
 * источник истины — модель: после каждого переключения строки
 * перечитываются из [GitRootsService.listRoots].
 */
class GitRootsDialog(private val project: Project) : DialogWrapper(project, true) {

    private val rootsPanel = JPanel(GridLayout(0, 1))

    init {
        title = GitRootsWidgetFactory.DISPLAY_NAME
        setOKButtonText("Close")
        init()
        PlatformVcsMappingService.get(project).requestRescan()
        rebuild()
    }

    override fun createCenterPanel(): JComponent {
        rebuild()
        return rootsPanel
    }

    override fun createActions(): Array<out Action> = arrayOf(okAction)

    private fun rebuild() {
        rootsPanel.removeAll()
        val roots = GitRootsService.get(project).listRoots().sortedBy { it.directory }
        if (roots.isEmpty()) {
            rootsPanel.add(JLabel("VCS roots not found"))
        } else {
            for (root in roots) {
                val checkbox = JBCheckBox(displayPath(root), root.tracked)
                checkbox.addItemListener {
                    GitRootsService.get(project).setTracked(root, checkbox.isSelected)
                    rebuild()
                }
                rootsPanel.add(checkbox)
            }
        }
        rootsPanel.revalidate()
        rootsPanel.repaint()
    }

    private fun displayPath(root: ManagedRoot): String {
        if (root.directory.isEmpty()) return PROJECT_LABEL
        val base = project.basePath ?: return root.directory
        val relative = FileUtil.getRelativePath(File(base), File(root.directory))
        return relative?.takeIf { it.isNotEmpty() } ?: root.directory
    }

    private companion object {
        const val PROJECT_LABEL = "<Project>"
    }
}
