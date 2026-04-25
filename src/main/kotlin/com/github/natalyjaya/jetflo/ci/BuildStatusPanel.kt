package com.github.natalyjaya.jetflo.ci

import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBScrollPane
import java.awt.*
import javax.swing.*

class BuildStatusPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val statusLabel = JLabel("No build run yet").apply {
        font = Font("SansSerif", Font.BOLD, 13)
        border = BorderFactory.createEmptyBorder(8, 12, 4, 12)
    }

    private val failureList = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = Color(30, 30, 30)
    }

    private val spinner = JLabel("⏳ Building…").apply {
        isVisible = false
        font = Font("SansSerif", Font.ITALIC, 12)
        border = BorderFactory.createEmptyBorder(4, 12, 4, 12)
    }

    init {
        background = Color(30, 30, 30)
        add(statusLabel, BorderLayout.NORTH)
        add(spinner, BorderLayout.CENTER)
        val scroll = JBScrollPane(failureList).apply {
            border = BorderFactory.createEmptyBorder()
            preferredSize = Dimension(300, 200)
        }
        add(scroll, BorderLayout.SOUTH)

        // Register this instance so the checkin handler can reach it
        instance = this
    }

    fun showRunning() = onEdt {
        statusLabel.text = "⏳ Running CI…"
        statusLabel.foreground = Color(200, 200, 0)
        spinner.isVisible = true
        failureList.removeAll()
        revalidate(); repaint()
    }

    fun showResult(result: BuildResult) = onEdt {
        spinner.isVisible = false
        failureList.removeAll()

        if (result.success) {
            statusLabel.text = "Build & tests passed \uD83D\uDC4E\uD83D\uDC4E\uD83D\uDC4E"
            statusLabel.foreground = Color(80, 200, 80)
        } else {
            statusLabel.text = "❌ ${result.failures.size} test(s) failed"
            statusLabel.foreground = Color(220, 80, 80)

            result.failures.forEach { f ->
                val card = JPanel(BorderLayout()).apply {
                    background = Color(50, 30, 30)
                    border = BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, Color(220, 80, 80)),
                        BorderFactory.createEmptyBorder(6, 8, 6, 8)
                    )
                    maximumSize = Dimension(Int.MAX_VALUE, 80)
                }
                val title = JLabel("${f.className.substringAfterLast('.')}#${f.testName}").apply {
                    font = Font("Monospaced", Font.BOLD, 11)
                    foreground = Color(255, 120, 120)
                }
                val msg = JLabel("<html>${f.message.take(120)}</html>").apply {
                    font = Font("SansSerif", Font.PLAIN, 11)
                    foreground = Color(200, 180, 180)
                }
                val lineHint = f.lineNumber?.let {
                    JLabel("line $it").apply {
                        font = Font("Monospaced", Font.ITALIC, 10)
                        foreground = Color(160, 160, 200)
                    }
                }
                card.add(title, BorderLayout.NORTH)
                card.add(msg, BorderLayout.CENTER)
                lineHint?.let { card.add(it, BorderLayout.SOUTH) }
                failureList.add(card)
                failureList.add(Box.createVerticalStrut(4))
            }
        }
        revalidate(); repaint()
    }

    private fun onEdt(block: () -> Unit) =
        ApplicationManager.getApplication().invokeLater(block)

    companion object {
        var instance: BuildStatusPanel? = null
    }
}