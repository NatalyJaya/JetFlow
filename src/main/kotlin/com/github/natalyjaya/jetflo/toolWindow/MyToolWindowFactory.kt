package com.github.natalyjaya.jetflo.toolWindow

import com.github.natalyjaya.jetflo.MyBundle
import com.github.natalyjaya.jetflo.ci.BuildStatusPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.JLabel

class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentManager = toolWindow.contentManager

        val mainContent = contentManager.factory.createContent(
            JLabel(MyBundle.message("randomLabel", "—")),
            "JetFlo",
            false
        )
        contentManager.addContent(mainContent)

        val ciPanel = BuildStatusPanel(project)
        val ciContent = contentManager.factory.createContent(ciPanel, "CI Status", false)
        contentManager.addContent(ciContent)
    }
}