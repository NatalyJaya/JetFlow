package com.github.natalyjaya.jetflo.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.panel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.swing.JPanel
import com.github.natalyjaya.jetflo.auth.AuthManager // Asegúrate de que el import sea correcto

class FlowBridgeToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val mainPanel = FlowBridgeMainPanel(project)
        val content = contentFactory.createContent(mainPanel, "Deploy Center", false)
        toolWindow.contentManager.addContent(content)
    }
}

class FlowBridgeMainPanel(project: Project) : JPanel() {
    private val renderHookField = JBTextField()
    private val tokenField = JBPasswordField()

    init {
        val myPanel = panel {
            group("Credential Configuration") {
                row("GitHub PAT:") {
                    cell(tokenField).comment("For GitHub Pages")
                }
                row("Render Hook:") {
                    cell(renderHookField).comment("URL de Deploy Hook de Render")
                }
                row {
                    button("Save settings") {
                        val token = String(tokenField.password)
                        if (token.isNotBlank()) {
                            ApplicationManager.getApplication().executeOnPooledThread {
                                AuthManager.saveToken(token)
                                ApplicationManager.getApplication().invokeLater {
                                    Messages.showInfoMessage("Settings save correctly.", "JetFlo")
                                }
                            }
                        }
                    }
                }
            }

            separator()

            group("Fast Deploy") {
                row {
                    label("Select one platform to deploy:").bold()
                }

                row {
                    button("Render.com") {
                        val hookUrl = renderHookField.text
                        if (hookUrl.isNotBlank()) {
                            executeRenderDeploy(hookUrl)
                        } else {
                            Messages.showErrorDialog("Please enter the Render Hook URL", "Error")
                        }
                    }.applyToComponent {
                        icon = AllIcons.Nodes.PpWeb
                    }
                }

                row {
                    button("Vercel (Production)") {
                        executeDeploy("Vercel")
                    }.applyToComponent {
                        icon = AllIcons.Nodes.Deploy
                    }
                }

                row {
                    button("GitHub Pages") {
                        executeDeploy("GitHub Pages")
                    }.applyToComponent {
                        icon = AllIcons.Vcs.Branch
                    }
                }
            }
        }
        add(myPanel)
    }

    private fun executeDeploy(platform: String) {
        Messages.showInfoMessage(
            "Starting deployment on $platform...\nRocky is preparing the packages.",
            "JetFlo Deploy"
        )
    }

    private fun executeRenderDeploy(hookUrl: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(hookUrl)
                .post("".toRequestBody())
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    ApplicationManager.getApplication().invokeLater {
                        if (response.isSuccessful) {
                            Messages.showInfoMessage("Render deployment requested successfully!", "Render Deploy")
                        } else {
                            Messages.showErrorDialog("Error in Render: ${response.code}", "Error in Deployment")
                        }
                    }
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog("Network error: ${e.message}", "Critical Error")
                }
            }
        }
    }
}