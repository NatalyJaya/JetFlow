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

private const val WIDGET_W = 320
private const val WIDGET_H = 280
private const val ROCKY_W  = 80
private const val ROCKY_H  = 80
private const val MARGIN   = 10

private const val POLL_INTERVAL_MS = 10_000L

// ── Sprites ───────────────────────────────────────────────────────────────────
private val WALK_FRAMES = arrayOf("/icons/walkleft1.png", "/icons/walkleft2.png")
private val JAZZ_FRAMES = arrayOf("/icons/jazz1.png", "/icons/jazz2.png", "/icons/jazz3.png")
private const val WALK_FRAME_MS = 150
private const val JAZZ_FRAME_MS = 120

// ─────────────────────────────────────────────────────────────────────────────
class RockyFloatingActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        SwingUtilities.invokeLater {
            val frame = WindowManager.getInstance().getFrame(project) ?: return@invokeLater
            val layeredPane = frame.layeredPane

            val rockyWidget = RockyWidget(project, layeredPane)
            layeredPane.add(rockyWidget, JLayeredPane.POPUP_LAYER)

            fun reposition() {
                val lh = layeredPane.height
                rockyWidget.setBounds(MARGIN, (lh - WIDGET_H - 20).coerceAtLeast(0), WIDGET_W, WIDGET_H)
            }

            reposition()
            layeredPane.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) = reposition()
            })

            // --- FLUJO DE MENSAJES LENTO ---
            // 1. Saludo inicial
            Timer(1500) {
                rockyWidget.showMessage("Hi! I'm Rocky") {
                    Timer(2000) {
                        rockyWidget.showMessage("Click me when\nyou need help!")
                    }.apply { isRepeats = false; start() }
                }
            }.apply { isRepeats = false; start() }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
private enum class RockyPhase {
    IDLE,       // Caminando — espera clic
    CHOICE,     // Píldoras CI/CD dentro de la burbuja
    CI,         // Selector de stacks
    CD_PROMPT,  // Pidiendo Render API key
    CD_PICK,    // Eligiendo servicio
    CD_READY,   // Deploy button visible
    DEPLOYING   // Deploy en curso
}

// ─────────────────────────────────────────────────────────────────────────────
private enum class SpriteMode { STAND, WALK, JAZZ }

// ─────────────────────────────────────────────────────────────────────────────
class RockyWidget(
    private val project: Project,
    private val layeredPane: JLayeredPane
) : JPanel(null) {

    // ── Bubble ────────────────────────────────────────────────────────────────
    private var bubbleLines: List<String> = emptyList()
    private var isTalking    = false
    private var isLoading    = false
    private var visibleChars = 0
    private var fullText     = ""

    // ── Sprites ───────────────────────────────────────────────────────────────
    private fun loadImg(path: String): Image? =
        javaClass.getResourceAsStream(path)?.use { ImageIcon(it.readBytes()).image }

    private val imgStand = loadImg("/icons/stand.png")
    private val imgWalk  = WALK_FRAMES.map { loadImg(it) }
    private val imgJazz  = JAZZ_FRAMES.map { loadImg(it) }

    private var spriteMode  = SpriteMode.WALK
    private var spriteFrame = 0
    private var spriteTimer: Timer? = null
    private var jazzCycles  = 0

    // ── Bob (parado, sin hablar) ───────────────────────────────────────────────
    private var bobOffset    = 0f
    private var bobDirection = 1

    // ── Idle timeout ──────────────────────────────────────────────────────────
    private var idleTimer: Timer? = null
    private val IDLE_TIMEOUT_MS = 8_000

    // ── Phase ─────────────────────────────────────────────────────────────────
    private var phase = RockyPhase.IDLE

    // ── Bounding boxes píldoras (calculadas en paint) ─────────────────────────
    private var ciPillRect:  Rectangle? = null
    private var cdPillRect:  Rectangle? = null
    private var hoveredPill: String?    = null

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
    private val deployBtn = JButton("Deploy").apply {
        isVisible = false
        background = Color(34, 197, 94); foreground = Color.WHITE
        isFocusPainted = false; font = Font("SansSerif", Font.BOLD, 11)
    }
    private val rollbackBtn = JButton("↩ Rollback").apply {
        isVisible = false
        background = Color(239, 68, 68); foreground = Color.WHITE
        isFocusPainted = false; font = Font("SansSerif", Font.BOLD, 11)
    }

    private var lastDeployId: String? = null

    // Movimiento
    private var walkX         = MARGIN.toFloat()
    private var walkDirection = 1  // 1 = derecha, -1 = izquierda
    private var walkTimer: Timer? = null

    private val randomPhrases = listOf(
        "You sleep,\nI watch.",
        "Thumbs up, baby \uD83D\uDC4E \uD83D\uDC4E \uD83D\uDC4E",
        "Fist my bump",
        "Grace question is dumb",
        "Only us.",
        "Rocky, Grace, big science",
        "Good, Good",
        "Oh, humor. \nConfusing.",
        "Dirty, dirty, dirty…. \nThis room for garbage?",
        "Amaze, Amaze, Amaze",
        "Grace Rocky Save Stars"
    )
    private var phraseTimer: Timer? = null

    // ─────────────────────────────────────────────────────────────────────────
    init {
        isOpaque = false

        val comboY = WIDGET_H - ROCKY_H - 10
        stackCombo.setBounds(0, comboY, WIDGET_W - 54, 30)
        applyBtn.setBounds(WIDGET_W - 50, comboY, 44, 30)

        val cdRowY = WIDGET_H - ROCKY_H - 68
        deployBtn.setBounds(0, cdRowY, WIDGET_W / 2 - 2, 26)
        rollbackBtn.setBounds(WIDGET_W / 2 + 2, cdRowY, WIDGET_W / 2 - 2, 26)

        add(stackCombo); add(applyBtn)
        add(deployBtn);  add(rollbackBtn)

        applyBtn.addActionListener    { onApply()    }
        deployBtn.addActionListener   { onDeploy()   }
        rollbackBtn.addActionListener { onRollback() }

        // Arrancar caminando
        setSpriteMode(SpriteMode.WALK)
        startBobAnimation()
        startWalking()

        // Registrar instancia para el CI poller
        instance = this

        // Clic en Rocky (IDLE) → menú; clic en píldoras (CHOICE) → elección
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                when (phase) {
                    RockyPhase.IDLE   -> showChoiceMenu()
                    RockyPhase.CHOICE -> {
                        if (ciPillRect?.contains(e.point) == true) onChooseCI()
                        else if (cdPillRect?.contains(e.point) == true) onChooseCD()
                    }
                    else -> {}
                }
            }
        })
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                if (phase != RockyPhase.CHOICE) return
                val prev = hoveredPill
                hoveredPill = when {
                    ciPillRect?.contains(e.point) == true -> "CI"
                    cdPillRect?.contains(e.point) == true -> "CD"
                    else -> null
                }
                cursor = if (hoveredPill != null) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                else Cursor.getDefaultCursor()
                if (hoveredPill != prev) repaint()
            }
        })

        if (RenderCredentialsStore.isConfigured(project)) {
            phase = RockyPhase.CD_READY
            setSpriteMode(SpriteMode.STAND)
            showCdReadyUi()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Sprite control
    // ─────────────────────────────────────────────────────────────────────────

    private fun setSpriteMode(mode: SpriteMode, onDone: (() -> Unit)? = null) {
        spriteTimer?.stop()
        spriteMode  = mode
        spriteFrame = 0

        val (frames, ms) = when (mode) {
            SpriteMode.WALK  -> imgWalk to WALK_FRAME_MS
            SpriteMode.JAZZ  -> imgJazz to JAZZ_FRAME_MS
            SpriteMode.STAND -> { repaint(); return }
        }

        spriteTimer = Timer(ms) {
            spriteFrame = (spriteFrame + 1) % frames.size
            // JAZZ: después de 2 ciclos completos vuelve a STAND y llama onDone
            if (mode == SpriteMode.JAZZ && spriteFrame == 0) {
                jazzCycles++
                if (jazzCycles >= 5) {  // 5 ciclos completos
                    jazzCycles = 0
                    setSpriteMode(SpriteMode.STAND)
                    onDone?.invoke()
                }
            }
            repaint()
        }.also { it.start() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Bob (parado, sin hablar)
    // ─────────────────────────────────────────────────────────────────────────
    private fun startBobAnimation() {
        Timer(50) {
            if (!isTalking && !isLoading && spriteMode == SpriteMode.STAND) {
                bobOffset += bobDirection * 0.6f
                if (bobOffset > 4f || bobOffset < -4f) bobDirection *= -1
                repaint()
            }
        }.start()
    }

    // funcion caminar

    fun startWalking() {
        walkTimer?.stop()
        walkTimer = Timer(30) {
            if (phase != RockyPhase.IDLE || isTalking) return@Timer

            val parentW = layeredPane.width
            val maxX    = (parentW - WIDGET_W - MARGIN).toFloat()

            walkX += walkDirection * 1.2f

            if (walkX >= maxX) {
                walkX = maxX
                walkDirection = -1
                // Girar sprite (walk frames hacia la otra dirección)
            } else if (walkX <= MARGIN) {
                walkX = MARGIN.toFloat()
                walkDirection = 1
            }

            val currentY = y
            setBounds(walkX.toInt(), currentY, WIDGET_W, WIDGET_H)
        }.also { it.start() }

        // Frases random cada 15-25 segundos
        schedulNextPhrase()
    }

    private fun schedulNextPhrase() {
        phraseTimer?.stop()
        val delay = (10_000..15_000).random()
        phraseTimer = Timer(delay) {
            if (phase == RockyPhase.IDLE && !isTalking) {
                if ((0..2).random() == 0) {
                    // Baila Y habla a la vez
                    walkTimer?.stop()
                    showMessage(randomPhrases.random())
                    setSpriteMode(SpriteMode.JAZZ) {
                        setSpriteMode(SpriteMode.WALK)
                        startWalking()
                    }
                }
            }
            schedulNextPhrase()
        }.apply { isRepeats = false; start() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  IDLE / CHOICE
    // ─────────────────────────────────────────────────────────────────────────

    private fun showChoiceMenu() {
        walkTimer?.stop()
        phraseTimer?.stop()
        phase       = RockyPhase.CHOICE
        isTalking   = true
        fullText    = "What do you need?"
        visibleChars = fullText.length
        bubbleLines = listOf(fullText)
        repaint()

        idleTimer?.stop()
        idleTimer = Timer(IDLE_TIMEOUT_MS) {
            SwingUtilities.invokeLater { returnToIdle() }
        }.apply { isRepeats = false; start() }
    }

    private fun onChooseCI() {
        idleTimer?.stop()
        resetPills()
        phase = RockyPhase.CI
        showMessage("Great! Let me fetch\nthe CI stacks for you.")
        Timer(800) { loadStacksFromGitHub() }.apply { isRepeats = false; start() }
    }

    private fun onChooseCD() {
        idleTimer?.stop()
        resetPills()
        phase = RockyPhase.CD_PROMPT
        setSpriteMode(SpriteMode.STAND)
        showMessage("Let's configure\nyour deployment!") {
            Timer(1200) { startCdFlow() }.apply { isRepeats = false; start() }
        }
    }

    private fun returnToIdle() {
        phase       = RockyPhase.IDLE
        isTalking   = false
        bubbleLines = emptyList()
        resetPills()
        setSpriteMode(SpriteMode.WALK)
        startWalking()
        repaint()
    }

    private fun resetPills() {
        ciPillRect  = null
        cdPillRect  = null
        hoveredPill = null
        cursor      = Cursor.getDefaultCursor()
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
                    if (phase == RockyPhase.CI) {
                        setSpriteMode(SpriteMode.STAND)
                        stackCombo.removeAllItems()
                        stackCombo.addItem("— Select a stack —")
                        names.forEach { stackCombo.addItem(it) }
                        stackCombo.isVisible = true; applyBtn.isVisible = true
                        stackCombo.isEnabled = true; applyBtn.isEnabled = true
                        showMessage("Stacks ready!\nPick one to set up CI.")
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
                    isLoading            = false
                    stackCombo.isVisible = false
                    applyBtn.isVisible   = false

                    // ── CI terminado: jazz y vuelve a IDLE ────────────────────
                    // NO redirige a CD — el usuario lo pedirá cuando quiera
                    setSpriteMode(SpriteMode.JAZZ)
                    showMessage("CI is live! \nClick me again for CD.") {
                        Timer(1000) {
                            phase = RockyPhase.IDLE
                            setSpriteMode(SpriteMode.WALK)
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
    //  CD Phase
    // ─────────────────────────────────────────────────────────────────────────

    private fun startCdFlow() {
        if (RenderCredentialsStore.hasApiKey()) {
            pickRenderService(RenderCredentialsStore.getApiKey()!!)
        } else {
            promptApiKey()
        }
    }

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
                returnToIdle()
                return@Timer
            }
            val key = String(field.password).trim()
            if (key.isBlank()) {
                showMessage("No key entered.\nOpen JetFlo panel to try again.")
                returnToIdle()
                return@Timer
            }
            RenderCredentialsStore.saveApiKey(key)
            showMessage("Got it! Fetching your\nRender services...")
            pickRenderService(key)
        }.apply { isRepeats = false; start() }
    }

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

                    val options = arrayOf("Link existing service", "Create new on Render", "Skip for now")
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
                        1    -> showCreateServiceDialog(apiKey)
                        else -> { showMessage("OK! Click me again\nto deploy later."); returnToIdle() }
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    showMessage("Render API error.\nCheck your key in JetFlo panel.")
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun showCreateServiceDialog(apiKey: String) {
        val nameField   = JTextField(project.name)
        val repoField   = JTextField("https://github.com/user/repo")
        val branchField = JTextField("main")

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
            showMessage("Skipped. Click me again\nto set up CD later.")
            returnToIdle()
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

    private fun bindService(serviceId: String, name: String) {
        RenderCredentialsStore.saveServiceId(project, serviceId)
        phase = RockyPhase.CD_READY
        setSpriteMode(SpriteMode.JAZZ)
        showMessage("All set! \nReady to deploy \"$name\".") {
            showCdReadyUi()
        }
    }

    private fun showCdReadyUi() {
        setSpriteMode(SpriteMode.STAND)
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
                    if (status.isSuccess) setSpriteMode(SpriteMode.JAZZ)
                    revalidate(); repaint()
                }
                break
            }
        }
    }

    private fun updateDeployBubble(status: DeployStatus) {
        val msg = when {
            status.isSuccess -> "Deploy successful! \nYou're live on Render!"
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
    //  showMessage
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
    //  Paint
    // ─────────────────────────────────────────────────────────────────────────

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // ── Imagen actual ─────────────────────────────────────────────────────
        val img = when (spriteMode) {
            SpriteMode.STAND -> imgStand
            SpriteMode.WALK  -> imgWalk.getOrNull(spriteFrame)
            SpriteMode.JAZZ  -> imgJazz.getOrNull(spriteFrame)
        }

        val isMoving = spriteMode != SpriteMode.STAND
        val rX = (width - ROCKY_W) / 2
        val rY = height - ROCKY_H - 4 + (if (!isMoving && !isTalking) bobOffset.toInt() else 0)

        // ── Burbuja ───────────────────────────────────────────────────────────
        val showBubble = bubbleLines.isNotEmpty() || phase == RockyPhase.CHOICE
        val isChoice   = phase == RockyPhase.CHOICE

        if (showBubble) {
            val pad     = 14
            val pillH   = 28
            val pillGap = 8
            val cornerR = 18

            g2.font = Font("SansSerif", Font.BOLD, 12)
            val fm  = g2.fontMetrics

            val pillLabelCI = "Set up CI"
            val pillLabelCD = "Set up CD"
            val pillWCI     = fm.stringWidth(pillLabelCI) + pad * 2
            val pillWCD     = fm.stringWidth(pillLabelCD) + pad * 2
            val pillsRowW   = pillWCI + pillGap + pillWCD

            val textMaxW   = bubbleLines.maxOfOrNull { fm.stringWidth(it) } ?: 0
            val contentW   = if (isChoice) maxOf(textMaxW, pillsRowW) else textMaxW
            val bW         = (contentW + pad * 2).coerceAtLeast(80)
            val textBlockH = bubbleLines.size * fm.height + pad * 2
            val bH         = if (isChoice) textBlockH + pillH + pillGap + pad else textBlockH

            val bX = ((width - bW) / 2).coerceAtLeast(2)
            val bY = (rY - bH - 12).coerceAtLeast(2)

            // Fondo + borde
            g2.color = Color(255, 255, 255, 245)
            g2.fillRoundRect(bX, bY, bW, bH, cornerR, cornerR)
            g2.stroke = BasicStroke(1.5f)
            g2.color = Color(160, 100, 60, 90)
            g2.drawRoundRect(bX, bY, bW, bH, cornerR, cornerR)

            // Texto (typewriter)
            g2.color = Color(30, 30, 30)
            bubbleLines.forEachIndexed { i, line ->
                g2.drawString(
                    line,
                    bX + (bW - fm.stringWidth(line)) / 2,
                    bY + pad + fm.ascent + i * fm.height
                )
            }

            // ── Píldoras CI / CD ──────────────────────────────────────────────
            if (isChoice) {
                val pillY  = bY + textBlockH + pillGap / 2
                val startX = bX + (bW - pillsRowW) / 2

                // Píldora CI (morada)
                val ciX     = startX
                val ciColor = if (hoveredPill == "CI") Color(120, 70, 40) else Color(160, 100, 60)
                g2.color = Color(160, 100, 60, 30)
                g2.fillRoundRect(ciX + 2, pillY + 3, pillWCI, pillH, 14, 14)
                g2.color = ciColor
                g2.fillRoundRect(ciX, pillY, pillWCI, pillH, 14, 14)
                g2.color = Color(255, 255, 255, 50)
                g2.fillRoundRect(ciX + 2, pillY + 2, pillWCI - 4, pillH / 2 - 2, 12, 12)
                g2.color = Color.WHITE
                g2.drawString(
                    pillLabelCI,
                    ciX + (pillWCI - fm.stringWidth(pillLabelCI)) / 2,
                    pillY + (pillH + fm.ascent - fm.descent) / 2
                )
                ciPillRect = Rectangle(ciX, pillY, pillWCI, pillH)

                // Píldora CD (verde)
                val cdX     = ciX + pillWCI + pillGap
                val cdColor = if (hoveredPill == "CD") Color(150, 50, 50) else Color(190, 80, 70)
                g2.color = Color(190, 80, 70, 30)
                g2.fillRoundRect(cdX + 2, pillY + 3, pillWCD, pillH, 14, 14)
                g2.color = cdColor
                g2.fillRoundRect(cdX, pillY, pillWCD, pillH, 14, 14)
                g2.color = Color(255, 255, 255, 50)
                g2.fillRoundRect(cdX + 2, pillY + 2, pillWCD - 4, pillH / 2 - 2, 12, 12)
                g2.color = Color.WHITE
                g2.drawString(
                    pillLabelCD,
                    cdX + (pillWCD - fm.stringWidth(pillLabelCD)) / 2,
                    pillY + (pillH + fm.ascent - fm.descent) / 2
                )
                cdPillRect = Rectangle(cdX, pillY, pillWCD, pillH)

            } else {
                ciPillRect = null
                cdPillRect = null
            }
        }

        // ── Rocky ─────────────────────────────────────────────────────────────
        if (img != null) g2.drawImage(img, rX, rY, ROCKY_W, ROCKY_H, null)
    }

    companion object {
        var instance: RockyWidget? = null
    }
}