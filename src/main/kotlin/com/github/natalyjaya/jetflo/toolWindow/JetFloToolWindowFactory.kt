package com.github.natalyjaya.jetflo.toolWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.panel
import javax.swing.JPanel
import com.intellij.ui.components.JBPasswordField
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages

class FlowBridgeToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        // panel principal
        val mainPanel = FlowBridgeMainPanel(project)

        val content = contentFactory.createContent(mainPanel, "GitHub & Render", false)
        toolWindow.contentManager.addContent(content)
    }
}

class FlowBridgeMainPanel(project: Project) : JPanel() {
    init {
        val tokenField = JBPasswordField()

        val myPanel = panel {
            // Sección de Configuración
            group("Configuración") {
                row("GitHub PAT:") {
                    cell(tokenField).comment("Token con permisos de 'repo' y 'workflow'")
                }
                row {
                    button("Guardar Credenciales") {
                        val token = String(tokenField.password)
                        if (token.isNotBlank()) {

                            // Enviamos la tarea "lenta" a un hilo secundario (PooledThread)
                            ApplicationManager.getApplication().executeOnPooledThread {

                                AuthManager.saveToken(token)

                                // Volvemos al hilo de la interfaz (EDT) para mostrar el pop-up visual
                                ApplicationManager.getApplication().invokeLater {
                                    Messages.showInfoMessage(
                                        "¡Token guardado de forma segura en la bóveda de IntelliJ!",
                                        "JetFlo Security"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            separator()

            // Sección del Monitor (que llenaremos luego)
            group("Monitor CI/CD") {
                row {
                    label("Status: Esperando conexión...").bold()
                }
                row {
                    button("Cargar Workflows") {
                        // 1. Nos vamos al hilo de fondo para leer la contraseña de forma segura
                        ApplicationManager.getApplication().executeOnPooledThread {
                            val token = AuthManager.getToken()

                            if (token.isNullOrBlank()) {
                                // Volvemos a la UI para avisar que falta el token
                                ApplicationManager.getApplication().invokeLater {
                                    Messages.showWarningDialog(
                                        "Por favor, guarda tu token de GitHub primero.",
                                        "Token No Encontrado"
                                    )
                                }
                                return@executeOnPooledThread
                            }

                            println("Token recuperado con éxito. Listo para llamar a GitHub.")

                            // AQUÍ HAREMOS LA LLAMADA HTTP A GITHUB
                        }
                    }
                }
            }
        }
        add(myPanel)
    }
}

