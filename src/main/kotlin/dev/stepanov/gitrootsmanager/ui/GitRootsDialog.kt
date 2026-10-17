package dev.stepanov.gitrootsmanager.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.components.JBCheckBox
import dev.stepanov.gitrootsmanager.core.GitRootsService
import dev.stepanov.gitrootsmanager.model.ManagedRoot
import dev.stepanov.gitrootsmanager.platform.PlatformVcsMappingService
import java.awt.BorderLayout
import java.awt.GridLayout
import java.io.File
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Список git-корней с мгновенным применением по клику чекбокса и
 * кнопкой «включить/выключить все». Чтение списка и применение — асинхронные
 * (детекция в режиме `<Project>` сканирует ФС вне EDT), перерисовка — по
 * колбэку сервиса.
 */
class GitRootsDialog(private val project: Project) : DialogWrapper(project, true) {

    private val rootsPanel = JPanel(GridLayout(0, 1))
    private val toggleAllButton = JButton()
    private val content = JPanel(BorderLayout(0, DIALOG_VGAP))
    private var currentRoots: List<ManagedRoot> = emptyList()

    init {
        title = GitRootsWidgetFactory.DISPLAY_NAME
        setOKButtonText("Close")
        toggleAllButton.addActionListener { applyToAll() }
        content.add(toggleAllButton, BorderLayout.NORTH)
        content.add(rootsPanel, BorderLayout.CENTER)
        init()
        PlatformVcsMappingService.get(project).requestRescan()
        refresh()
    }

    override fun createCenterPanel(): JComponent {
        rootsPanel.add(JLabel(LOADING))
        return content
    }

    override fun createActions(): Array<out Action> = arrayOf(okAction)

    private fun refresh() {
        GitRootsService.get(project).listRootsAsync { roots -> render(roots) }
    }

    private fun render(roots: List<ManagedRoot>) {
        currentRoots = roots
        val toggleable = roots.filter { it.directory.isNotEmpty() }
        toggleAllButton.text = if (toggleable.any { !it.tracked }) ENABLE_ALL else DISABLE_ALL
        toggleAllButton.isVisible = toggleable.isNotEmpty()

        rootsPanel.removeAll()
        if (roots.isEmpty()) {
            rootsPanel.add(JLabel("VCS roots not found"))
        } else {
            roots.sortedBy { it.directory }.forEach { rootsPanel.add(rowFor(it)) }
        }
        rootsPanel.revalidate()
        rootsPanel.repaint()
    }

    private fun rowFor(root: ManagedRoot): JBCheckBox {
        val checkbox = JBCheckBox(displayPath(root), root.tracked)
        val toggleable = root.directory.isNotEmpty()
        checkbox.isEnabled = toggleable
        if (!toggleable) {
            checkbox.toolTipText = DEFAULT_MAPPING_TOOLTIP
        } else {
            checkbox.addItemListener {
                GitRootsService.get(project).setTracked(root, checkbox.isSelected) { refresh() }
            }
        }
        return checkbox
    }

    private fun applyToAll() {
        val target = currentRoots.any { it.directory.isNotEmpty() && !it.tracked }
        GitRootsService.get(project).setAllTracked(target, currentRoots) { refresh() }
    }

    private fun displayPath(root: ManagedRoot): String {
        if (root.directory.isEmpty()) return PROJECT_LABEL
        val base = project.basePath ?: return root.directory
        val relative = FileUtil.getRelativePath(File(base), File(root.directory))
        return relative?.takeIf { it.isNotEmpty() } ?: root.directory
    }

    private companion object {
        const val PROJECT_LABEL = "<Project>"
        const val ENABLE_ALL = "Включить все"
        const val DISABLE_ALL = "Выключить все"
        const val DIALOG_VGAP = 8
        const val LOADING = "Loading roots…"
        const val DEFAULT_MAPPING_TOOLTIP =
            "Маппинг всего проекта; при первом выключении любого репозитория будет развёрнут в явные"
    }
}
