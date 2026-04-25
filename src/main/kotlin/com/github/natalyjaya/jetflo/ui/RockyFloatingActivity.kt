package com.github.natalyjaya.jetflo.ui

import com.github.natalyjaya.jetflo.cd.RenderApiClient
import com.github.natalyjaya.jetflo.cd.RenderApiClient.DeployStatus
import com.github.natalyjaya.jetflo.cd.RenderCredentialsStore
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

private const val WIDGET_W = 200
private const val WIDGET_H = 220
private const val ROCKY_W  = 80
private const val ROCKY_H  = 80
private const val MARGIN   = 10

// Polling interval for deploy status (ms)
private const val POLL_INTERVAL_MS = 10_000L

class RockyFloatingActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        SwingUtilities.invokeLater {
            val frame = WindowManager.getInstance().getFrame(project) ?: return@invokeLater
            val layeredPane = frame.layeredPane

            val rockyWidget = RockyWidget(project, layeredPane)
            layeredPane.add(rockyWidget, JLayeredPane.PALETTE_LAYER)

            fun reposition() {
                val lh = layeredPane.height
                rockyWidget.setBounds(MARGIN, (lh - WIDGET_H - 40).coerceAtLeast(0), WIDGET_W, WIDGET_H)
            }

            reposition()
            layeredPane.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) = reposition()
            })

            Timer(1500) {
                rockyWidget.showMessage("Hi! I'm Rocky") {
                    Timer(2000) {
                        rockyWidget.showMessage("I'll help you with your CI.")
                    }.apply { isRepeats = false; start() }
                }
            }.apply { isRepeats = false; start() }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Internal state: which phase Rocky is in
// ─────────────────────────────────────────────────────────────────────────────
private enum class RockyPhase {
    CI,           // Showing stack selector — waiting for CI setup
    CD_PROMPT,    // CI done — asking for Render API key
    CD_PICK,      // API key stored — picking a service
    CD_READY,     // Fully configured — showing Deploy button
    DEPLOYING     // Deploy in progress — showing status
}

class RockyWidget(
    private val project: Project,
    private val layeredPane: JLayeredPane
) : JPanel(null) {

    // ── Bubble state (untouched) ───────────────────────────────────────────────
    private var bubbleLines: List<String> = emptyList()
    private var isTalking    = false
    private var isLoading    = false
    private var visibleChars = 0
    private var fullText     = ""

    // ── Bob animation ─────────────────────────────────────────────────────────
    private var bobOffset    = 0f
    private var bobDirection = 1

    // ── Rocky phase ───────────────────────────────────────────────────────────
    private var phase = RockyPhase.CI

    // ── CI widgets ────────────────────────────────────────────────────────────
    private val stackCombo = JComboBox<String>().apply {
        isVisible = false; isEnabled = false
        font = Font("SansSerif", Font.PLAIN, 12)
    }
    private val applyBtn = JButton("▶").apply {
        isVisible = false
        background = Color(88, 101, 242); foreground = Color.WHITE; isFocusPainted = false
    }

    // ── CD widgets ────────────────────────────────────────────────────────────
    private val deployBtn = JButton("🚀 Deploy").apply {
        isVisible = false
        background = Color(34, 197, 94); foreground = Color.WHITE
        isFocusPainted = false; font = Font("SansSerif", Font.BOLD, 11)
    }
    private val rollbackBtn = JButton("↩ Rollback").apply {
        isVisible = false
        background = Color(239, 68, 68); foreground = Color.WHITE
        isFocusPainted = false; font = Font("SansSerif", Font.BOLD, 11)
    }

    // ── Rocky image ───────────────────────────────────────────────────────────
    private val rockyImage: Image? =
        javaClass.getResource("/icons/stand.png")?.let { ImageIcon(it).image }

    // ── Last deploy id for rollback ───────────────────────────────────────────
    private var lastDeployId: String? = null

    // ─────────────────────────────────────────────────────────────────────────
    init {
        isOpaque = false

        // CI row
        val comboY = WIDGET_H - ROCKY_H - 35
        stackCombo.setBounds(0, comboY, WIDGET_W - 50, 30)
        applyBtn.setBounds(WIDGET_W - 46, comboY, 44, 30)

        // CD row (above CI row)
        val cdRowY = WIDGET_H - ROCKY_H - 68
        deployBtn.setBounds(0, cdRowY, WIDGET_W / 2 - 2, 26)
        rollbackBtn.setBounds(WIDGET_W / 2 + 2, cdRowY, WIDGET_W / 2 - 2, 26)

        add(stackCombo); add(applyBtn)
        add(deployBtn);  add(rollbackBtn)

        applyBtn.addActionListener    { onApply()    }
        deployBtn.addActionListener   { onDeploy()   }
        rollbackBtn.addActionListener { onRollback() }

        startBobAnimation()
        loadStacksFromGitHub()

        // If Render is already fully configured, skip CI and go straight to CD_READY
        if (RenderCredentialsStore.isConfigured(project)) {
            phase = RockyPhase.CD_READY
            showCdReadyUi()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Bob animation (original, untouched)
    // ─────────────────────────────────────────────────────────────────────────
    private fun startBobAnimation() {
        Timer(50) {
            if (!isTalking && !isLoading) {
                bobOffset += bobDirection * 0.6f
                if (bobOffset > 4f || bobOffset < -4f) bobDirection *= -1
                repaint()
            }
        }.start()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CI Phase
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadStacksFromGitHub() {
        Thread {
            try {
                val json  = fetchUrl(GITHUB_API_BASE)
                val names = JSONArray(json).let { arr ->
                    (0 until arr.length()).map { arr.getJSONObject(it).getString("name") }
                        .filter { it.endsWith(".yml") || it.endsWith(".yaml") }
                        .map { it.removeSuffix(".yml").removeSuffix(".yaml") }
                }
                Thread.sleep(7000)
                SwingUtilities.invokeLater {
                    // Only show the CI combo if we're still in CI phase
                    if (phase == RockyPhase.CI) {
                        stackCombo.removeAllItems()
                        stackCombo.addItem("— Select a stack —")
                        names.forEach { stackCombo.addItem(it) }
                        stackCombo.isVisible = true; applyBtn.isVisible = true
                        stackCombo.isEnabled = true; applyBtn.isEnabled = true
                        showMessage("Nice! Stacks are ready.\nPick one to implement CI.")
                        revalidate()
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    if (phase == RockyPhase.CI) showMessage("GitHub connection failed")
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun onApply() {
        val selected = stackCombo.selectedItem as? String ?: return
        if (selected.startsWith("—")) return
        isLoading = true; applyBtn.isEnabled = false
        showMessage("Setting up $selected CI...")

        Thread {
            try {
                val content = fetchUrl("$GITHUB_RAW_BASE/$selected.yml")
                createWorkflowFile(content)

                SwingUtilities.invokeLater {
                    isLoading = false
                    applyBtn.isEnabled = true

                    // ── CI done → hide CI widgets and bridge to CD ────────────
                    stackCombo.isVisible = false
                    applyBtn.isVisible   = false
                    phase = RockyPhase.CD_PROMPT

                    showMessage("CI is live! ✅\nNow let's set up CD!") {
                        // Wait a moment so the user reads the message, then start CD flow
                        Timer(1500) {
                            startCdFlow()
                        }.apply { isRepeats = false; start() }
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    isLoading = false; applyBtn.isEnabled = true
                    showMessage("Error downloading CI")
                }
            }
        }.apply { isDaemon = true; start() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CD Phase — step by step
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Entry point: asks for Render API Key if not already stored,
     * then proceeds to service picking.
     */
    private fun startCdFlow() {
        if (RenderCredentialsStore.hasApiKey()) {
            // Already have a key — jump to service selection
            pickRenderService(RenderCredentialsStore.getApiKey()!!)
        } else {
            promptApiKey()
        }
    }

    // ── Step 1: collect API key ───────────────────────────────────────────────
    private fun promptApiKey() {
        phase = RockyPhase.CD_PROMPT
        showMessage("Almost there!\nI need your Render API Key.")

        Timer(2000) {
            val field = JPasswordField(30)
            val panel = JPanel(BorderLayout(8, 8)).apply {
                add(JLabel("Render API Key (starts with rnd_):"), BorderLayout.NORTH)
                add(field, BorderLayout.CENTER)
                add(
                    JLabel("<html><small>dashboard.render.com → Account → API Keys</small></html>"),
                    BorderLayout.SOUTH
                )
            }
            val res = JOptionPane.showConfirmDialog(
                null, panel, "Connect Render for CD",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
            )
            if (res != JOptionPane.OK_OPTION) {
                showMessage("No worries!\nYou can set it later in JetFlo panel.")
                return@Timer
            }
            val key = String(field.password).trim()
            if (key.isBlank()) {
                showMessage("No key entered.\nOpen JetFlo panel to try again.")
                return@Timer
            }
            RenderCredentialsStore.saveApiKey(key)
            showMessage("Got it! Fetching your\nRender services...")
            pickRenderService(key)
        }.apply { isRepeats = false; start() }
    }

    // ── Step 2: list services and pick one (or create new) ───────────────────
    private fun pickRenderService(apiKey: String) {
        phase = RockyPhase.CD_PICK

        Thread {
            try {
                val services = RenderApiClient.listServices(apiKey)

                SwingUtilities.invokeLater {
                    if (services.isEmpty()) {
                        showCreateServiceDialog(apiKey)
                        return@invokeLater
                    }

                    // Try to auto-match by project name
                    val match = services.firstOrNull {
                        it.name.equals(project.name, ignoreCase = true)
                    }

                    if (match != null) {
                        val useIt = JOptionPane.showConfirmDialog(
                            null,
                            "Found \"${match.name}\" on Render.\nLink it to this project?",
                            "Auto-match found!",
                            JOptionPane.YES_NO_OPTION
                        )
                        if (useIt == JOptionPane.YES_OPTION) {
                            bindService(match.id, match.name)
                            return@invokeLater
                        }
                    }

                    // Manual pick or create new
                    val options = arrayOf(
                        "🔗 Link existing service",
                        "✨ Create new on Render",
                        "Skip for now"
                    )
                    when (JOptionPane.showOptionDialog(
                        null,
                        "Which Render service should I deploy to?",
                        "Pick a Render Service",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                        null, options, options[0]
                    )) {
                        0 -> {
                            val names  = services.map { it.name }.toTypedArray()
                            val chosen = JOptionPane.showInputDialog(
                                null, "Select the service:", "Link Service",
                                JOptionPane.PLAIN_MESSAGE, null, names, names[0]
                            ) as? String ?: return@invokeLater
                            val svc = services.first { it.name == chosen }
                            bindService(svc.id, svc.name)
                        }
                        1 -> showCreateServiceDialog(apiKey)
                        else -> showMessage("OK! Use the JetFlo panel\nwhen you're ready to deploy.")
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    showMessage("Render API error.\nCheck your key in JetFlo panel.")
                }
            }
        }.apply { isDaemon = true; start() }
    }

    // ── Create a new Render service ───────────────────────────────────────────
    private fun showCreateServiceDialog(apiKey: String) {
        val nameField  = JTextField(project.name)
        val repoField  = JTextField("https://github.com/user/repo")
        val branchField= JTextField("main")

        val form = JPanel(GridLayout(0, 2, 6, 6)).apply {
            add(JLabel("Service name:")); add(nameField)
            add(JLabel("GitHub repo URL:")); add(repoField)
            add(JLabel("Branch:")); add(branchField)
        }

        val res = JOptionPane.showConfirmDialog(
            null, form, "Create New Render Service",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        )
        if (res != JOptionPane.OK_OPTION) {
            showMessage("Skipped. Use the JetFlo\npanel to set up CD later.")
            return
        }

        val name = nameField.text.trim()
        val repo = repoField.text.trim()
        if (name.isBlank() || repo.isBlank()) {
            showMessage("Name and repo are required.")
            return
        }

        showMessage("Creating \"$name\"\non Render...")

        Thread {
            try {
                val serviceId = RenderApiClient.createService(
                    apiKey   = apiKey,
                    name     = name,
                    repoUrl  = repo,
                    branch   = branchField.text.trim().ifBlank { "main" }
                )
                SwingUtilities.invokeLater { bindService(serviceId, name) }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    showMessage("Create failed.\nTry again in JetFlo panel.")
                }
            }
        }.apply { isDaemon = true; start() }
    }

    // ── Bind service and show the Deploy button ───────────────────────────────
    private fun bindService(serviceId: String, name: String) {
        RenderCredentialsStore.saveServiceId(project, serviceId)
        phase = RockyPhase.CD_READY
        showMessage("All set! 🎉\nReady to deploy \"$name\".")
        Timer(2000) { showCdReadyUi() }.apply { isRepeats = false; start() }
    }

    private fun showCdReadyUi() {
        deployBtn.isVisible   = true
        rollbackBtn.isVisible = false
        revalidate(); repaint()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Deploy
    // ─────────────────────────────────────────────────────────────────────────

    private fun onDeploy() {
        val apiKey    = RenderCredentialsStore.getApiKey()    ?: return reconfigureRender()
        val serviceId = RenderCredentialsStore.getServiceId(project) ?: return reconfigureRender()

        phase = RockyPhase.DEPLOYING
        deployBtn.isEnabled   = false
        rollbackBtn.isVisible = false
        showMessage("Deploying to Render... ⏳")

        Thread {
            try {
                val deployId = RenderApiClient.triggerDeploy(apiKey, serviceId)
                lastDeployId = deployId
                pollDeployStatus(apiKey, serviceId, deployId)
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    phase = RockyPhase.CD_READY
                    deployBtn.isEnabled = true
                    showMessage("Deploy trigger failed.\nCheck JetFlo panel.")
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun pollDeployStatus(apiKey: String, serviceId: String, deployId: String) {
        while (true) {
            Thread.sleep(POLL_INTERVAL_MS)
            val status = try {
                RenderApiClient.getDeployStatus(apiKey, serviceId, deployId)
            } catch (_: Exception) { DeployStatus.UNKNOWN }

            SwingUtilities.invokeLater { updateDeployBubble(status) }

            if (status.isTerminal) {
                SwingUtilities.invokeLater {
                    phase = RockyPhase.CD_READY
                    deployBtn.isEnabled   = true
                    rollbackBtn.isVisible = status.isFailed
                    revalidate(); repaint()
                }
                break
            }
        }
    }

    private fun updateDeployBubble(status: DeployStatus) {
        val msg = when {
            status.isSuccess -> "Deploy successful! 🚀\nYou're live on Render!"
            status.isFailed  -> "Oh no! Deploy failed. ↩️\nPress Rollback to revert."
            status == DeployStatus.CANCELED -> "Deploy was canceled."
            else -> "Deploying... ⏳\n${status.name.lowercase().replace('_', ' ')}"
        }
        showMessage(msg)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Rollback
    // ─────────────────────────────────────────────────────────────────────────

    private fun onRollback() {
        val apiKey    = RenderCredentialsStore.getApiKey()    ?: return reconfigureRender()
        val serviceId = RenderCredentialsStore.getServiceId(project) ?: return reconfigureRender()
        val failedId  = lastDeployId ?: run { showMessage("No deploy to roll back."); return }

        rollbackBtn.isEnabled = false
        deployBtn.isEnabled   = false
        showMessage("Rolling back... ⏳")

        Thread {
            try {
                val rollbackDeployId = RenderApiClient.rollback(apiKey, serviceId, failedId)
                if (rollbackDeployId == null) {
                    SwingUtilities.invokeLater {
                        rollbackBtn.isEnabled = true; deployBtn.isEnabled = true
                        showMessage("No previous deploy\nfound to roll back to.")
                    }
                    return@Thread
                }
                lastDeployId = rollbackDeployId
                pollDeployStatus(apiKey, serviceId, rollbackDeployId)
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    rollbackBtn.isEnabled = true; deployBtn.isEnabled = true
                    showMessage("Rollback failed.\nSee JetFlo panel.")
                }
            }
        }.apply { isDaemon = true; start() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Credential recovery
    // ─────────────────────────────────────────────────────────────────────────

    private fun reconfigureRender() {
        RenderCredentialsStore.clearAll(project)
        deployBtn.isVisible   = false
        rollbackBtn.isVisible = false
        phase = RockyPhase.CD_PROMPT
        showMessage("Credentials missing.\nLet me re-configure...")
        Timer(2000) { startCdFlow() }.apply { isRepeats = false; start() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Shared helpers (original, untouched)
    // ─────────────────────────────────────────────────────────────────────────

    fun showMessage(text: String, onFinished: (() -> Unit)? = null) {
        isTalking    = true
        fullText     = text
        visibleChars = 0

        val typewriter = Timer(60, null)
        typewriter.addActionListener {
            if (visibleChars < fullText.length) {
                visibleChars++
                bubbleLines = fullText.substring(0, visibleChars).split("\n")
                repaint()
            } else {
                (it.source as Timer).stop()
                onFinished?.invoke()
                Timer(3500) {
                    if (fullText == text) {
                        isTalking   = false
                        bubbleLines = emptyList()
                        repaint()
                    }
                }.apply { isRepeats = false; start() }
            }
        }
        typewriter.start()
    }

    private fun createWorkflowFile(content: String) {
        val basePath = project.basePath ?: return
        val workflowDir = File("$basePath/.github/workflows").apply { mkdirs() }
        val targetFile  = File(workflowDir, "main.yml").apply { writeText(content) }
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

    // ─────────────────────────────────────────────────────────────────────────
    //  Paint (untouched)
    // ─────────────────────────────────────────────────────────────────────────
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val rX = (width - ROCKY_W) / 2
        val rY = height - ROCKY_H - 4 + (if (!isTalking) bobOffset.toInt() else -2)

        if (bubbleLines.isNotEmpty()) {
            val pad  = 12
            g2.font  = Font("SansSerif", Font.BOLD, 12)
            val fm   = g2.fontMetrics
            val maxW = bubbleLines.maxOf { fm.stringWidth(it) }
            val bW   = maxW + pad * 2
            val bH   = bubbleLines.size * fm.height + pad * 2
            val bX   = (width - bW) / 2
            val bY   = if (stackCombo.isVisible) stackCombo.y - bH - 10 else rY - bH - 10

            g2.color = Color(255, 255, 255, 250)
            g2.fillRoundRect(bX, bY, bW, bH, 18, 18)
            g2.color = Color(88, 101, 242, 80)
            g2.drawRoundRect(bX, bY, bW, bH, 18, 18)
            g2.color = Color.BLACK
            bubbleLines.forEachIndexed { i, line ->
                g2.drawString(
                    line,
                    bX + (bW - fm.stringWidth(line)) / 2,
                    bY + pad + fm.ascent + i * fm.height
                )
            }
        }

        if (rockyImage != null) {
            g2.drawImage(rockyImage, rX, rY, ROCKY_W, ROCKY_H, null)
        }
    }
}