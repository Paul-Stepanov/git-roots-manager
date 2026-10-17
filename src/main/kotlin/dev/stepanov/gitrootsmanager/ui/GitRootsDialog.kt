package dev.stepanov.gitrootsmanager.ui

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import dev.stepanov.gitrootsmanager.core.GitRootsService
import dev.stepanov.gitrootsmanager.model.ManagedRoot
import java.awt.BorderLayout
import java.awt.GridLayout
import java.io.File
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Список корней и мгновенное переключение. Во всех режимах детектор
 * сканирует ФС вне EDT; изменения и перерисовка выполняются на EDT по колбэку.
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
        val listContainer = JPanel(BorderLayout())
        listContainer.add(rootsPanel, BorderLayout.NORTH)
        content.add(JBScrollPane(listContainer), BorderLayout.CENTER)
        content.preferredSize = JBUI.size(480, 560)
        isResizable = true
        init()
        refresh()
    }

    override fun createCenterPanel(): JComponent {
        rootsPanel.add(JLabel(LOADING))
        return content
    }

    override fun createActions(): Array<out Action> = arrayOf(okAction)

    override fun getDimensionServiceKey(): String = "dev.stepanov.gitrootsmanager.dialog"

    private fun refresh() {
        GitRootsService.get(project).listRootsAsync(callbackModality()) { roots -> render(roots) }
    }

    private fun render(roots: List<ManagedRoot>) {
        currentRoots = roots
        val toggleable = roots.filter { it.toggleable }
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
        val toggleable = root.toggleable
        checkbox.isEnabled = toggleable
        if (!toggleable) {
            checkbox.toolTipText = if (root.directory.isEmpty()) DEFAULT_MAPPING_TOOLTIP else PROJECT_ROOT_TOOLTIP
        } else {
            checkbox.addItemListener {
                GitRootsService.get(project).setTracked(root, checkbox.isSelected, callbackModality()) { refresh() }
            }
        }
        return checkbox
    }

    private fun applyToAll() {
        val target = currentRoots.any { it.toggleable && !it.tracked }
        GitRootsService.get(project).setAllTracked(target, currentRoots, callbackModality()) { refresh() }
    }

    private fun callbackModality(): ModalityState {
        // Loading starts before show(); current() would capture a non-modal state.
        return ModalityState.any()
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
        const val PROJECT_ROOT_TOOLTIP = "Корень проекта; отслеживание не выключается"
        const val DEFAULT_MAPPING_TOOLTIP =
            "Маппинг всего проекта; при первом выключении репозитория " +
                "будет развёрнут в явные"
    }
}
