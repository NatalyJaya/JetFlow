package com.github.natalyjaya.jetflo.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.WindowManager
import java.awt.*
import java.awt.event.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.swing.*
import org.json.JSONArray

private const val GITHUB_API_BASE = "https://api.github.com/repos/actions/starter-workflows/contents/ci"
private const val GITHUB_RAW_BASE = "https://raw.githubusercontent.com/actions/starter-workflows/main/ci"

// Variables de tamaño unificadas
private const val WIDGET_W  = 200
private const val WIDGET_H  = 220
private const val ROCKY_W    = 80  // Asegúrate de que termine en Y
private const val ROCKY_H    = 80
private const val MARGIN     = 10

class RockyFloatingActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        SwingUtilities.invokeLater {
            val frame = WindowManager.getInstance().getFrame(project) ?: return@invokeLater
            val layeredPane = frame.layeredPane

            val rockyWidget = RockyWidget(project)
            layeredPane.add(rockyWidget, JLayeredPane.PALETTE_LAYER)

            fun reposition() {
                val lh = layeredPane.height
                rockyWidget.setBounds(MARGIN, (lh - WIDGET_H - 40).coerceAtLeast(0), WIDGET_W, WIDGET_H)
            }

            reposition()
            layeredPane.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) = reposition()
            })

            // --- FLUJO DE MENSAJES LENTO ---
            // 1. Saludo inicial
            Timer(1500) {
                rockyWidget.showMessage("Hi! I'm Rocky") {
                    // 2. Segunda parte tras 2 segundos de pausa
                    Timer(2000) {
                        rockyWidget.showMessage("I'll help you with your CI.")
                    }.apply { isRepeats = false; start() }
                }
            }.apply { isRepeats = false; start() }
        }
    }
}

class RockyWidget(private val project: Project) : JPanel(null) {
    private var bubbleLines: List<String> = emptyList()
    private var isTalking = false
    private var bobOffset = 0f
    private var bobDirection = 1
    private var isLoading = false
    private var visibleChars = 0
    private var fullText = ""

    private val rockyImage: Image? = javaClass.getResource("/icons/stand.png")?.let { ImageIcon(it).image }

    private val stackCombo = JComboBox<String>().apply {
        isVisible = false
        isEnabled = false
        font = Font("SansSerif", Font.PLAIN, 12)
    }

    private val applyBtn = JButton("▶").apply {
        isVisible = false
        background = Color(88, 101, 242)
        foreground = Color.WHITE
        isFocusPainted = false
    }

    init {
        isOpaque = false
        // Posicionamiento de UI
        val comboY = WIDGET_H - ROCKY_H - 35
        stackCombo.setBounds(0, comboY, WIDGET_W - 50, 30)
        applyBtn.setBounds(WIDGET_W - 46, comboY, 44, 30)

        add(stackCombo)
        add(applyBtn)

        applyBtn.addActionListener { onApply() }
        startBobAnimation()
        loadStacksFromGitHub()
    }

    private fun startBobAnimation() {
        Timer(50) {
            if (!isTalking && !isLoading) {
                bobOffset += bobDirection * 0.6f
                if (bobOffset > 4f || bobOffset < -4f) bobDirection *= -1
                repaint()
            }
        }.start()
    }

    fun showMessage(text: String, onFinished: (() -> Unit)? = null) {
        isTalking = true
        fullText = text
        visibleChars = 0

        val typewriter = Timer(60, null) // Velocidad de escritura lenta (60ms)
        typewriter.addActionListener {
            if (visibleChars < fullText.length) {
                visibleChars++
                bubbleLines = fullText.substring(0, visibleChars).split("\n")
                repaint()
            } else {
                (it.source as Timer).stop()
                onFinished?.invoke()
                // El mensaje se queda un tiempo antes de borrarse
                Timer(3500) {
                    if (fullText == text) {
                        isTalking = false
                        bubbleLines = emptyList()
                        repaint()
                    }
                }.apply { isRepeats = false; start() }
            }
        }
        typewriter.start()
    }

    private fun loadStacksFromGitHub() {
        Thread {
            try {
                val json = fetchUrl(GITHUB_API_BASE)
                val names = JSONArray(json).let { arr ->
                    (0 until arr.length()).map { arr.getJSONObject(it).getString("name") }
                        .filter { it.endsWith(".yml") || it.endsWith(".yaml") }
                        .map { it.removeSuffix(".yml").removeSuffix(".yaml") }
                }

                // Esperamos 7 segundos para que dé tiempo a las presentaciones iniciales
                Thread.sleep(7000)

                SwingUtilities.invokeLater {
                    stackCombo.removeAllItems()
                    stackCombo.addItem("— Select a stack —")
                    names.forEach { stackCombo.addItem(it) }

                    stackCombo.isVisible = true
                    applyBtn.isVisible = true
                    stackCombo.isEnabled = true
                    applyBtn.isEnabled = true

                    showMessage("Nice! Stacks are ready.\nPick one to implement CI.")
                    revalidate()
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater { showMessage("GitHub connection failed") }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun onApply() {
        val selected = stackCombo.selectedItem as? String ?: return
        if (selected.startsWith("—")) return
        isLoading = true
        applyBtn.isEnabled = false
        showMessage("Setting up $selected...")

        Thread {
            try {
                val content = fetchUrl("$GITHUB_RAW_BASE/$selected.yml")
                createWorkflowFile(content)
                SwingUtilities.invokeLater {
                    isLoading = false
                    applyBtn.isEnabled = true
                    showMessage("All set!\nCheck .github/workflows")
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    isLoading = false
                    applyBtn.isEnabled = true
                    showMessage("Error downloading")
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun createWorkflowFile(content: String) {
        val basePath = project.basePath ?: return
        val workflowDir = File("$basePath/.github/workflows").apply { mkdirs() }
        val targetFile = File(workflowDir, "main.yml").apply { writeText(content) }

        ApplicationManager.getApplication().invokeLater {
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFile)?.let { vf ->
                VfsUtil.markDirtyAndRefresh(false, false, false, vf)
            }
        }
    }

    private fun fetchUrl(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "JetFlo-Plugin")
        return conn.inputStream.bufferedReader().readText()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val rX = (width - ROCKY_W) / 2
        val rY = height - ROCKY_H - 4 + (if (!isTalking) bobOffset.toInt() else -2)

        if (bubbleLines.isNotEmpty()) {
            val pad = 12
            g2.font = Font("SansSerif", Font.BOLD, 12)
            val fm = g2.fontMetrics
            val maxW = bubbleLines.maxOf { fm.stringWidth(it) }
            val bW = maxW + pad * 2
            val bH = bubbleLines.size * fm.height + pad * 2
            val bX = (width - bW) / 2
            val bY = if (stackCombo.isVisible) stackCombo.y - bH - 10 else rY - bH - 10

            g2.color = Color(255, 255, 255, 250)
            g2.fillRoundRect(bX, bY, bW, bH, 18, 18)
            g2.color = Color(88, 101, 242, 80)
            g2.drawRoundRect(bX, bY, bW, bH, 18, 18)

            g2.color = Color.BLACK
            bubbleLines.forEachIndexed { i, line ->
                g2.drawString(line, bX + (bW - fm.stringWidth(line)) / 2, bY + pad + fm.ascent + i * fm.height)
            }
        }

        if (rockyImage != null) {
            g2.drawImage(rockyImage, rX, rY, ROCKY_W, ROCKY_H, null)
        }
    }
}