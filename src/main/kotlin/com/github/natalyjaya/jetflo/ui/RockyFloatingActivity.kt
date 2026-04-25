package com.github.natalyjaya.jetflo.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.WindowManager
import java.awt.*
import java.awt.event.*
import javax.swing.*

class RockyFloatingActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        SwingUtilities.invokeLater {
            val frame = WindowManager.getInstance().getFrame(project) ?: return@invokeLater
            val layeredPane = frame.layeredPane
            val rockyWidget = RockyWidget()
            layeredPane.add(rockyWidget, JLayeredPane.PALETTE_LAYER)

            fun reposition() {
                val fh = layeredPane.height
                // Aumentamos el tamaño del widget para que quepan los botones de CI
                val w = 250
                val h = 220
                rockyWidget.setBounds(20, fh - h - 40, w, h)
            }

            reposition()
            layeredPane.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) = reposition()
            })
        }
    }
}

class RockyWidget : JPanel() {
    // Estados de Rocky
    private enum class State { GREETING, ASKING_CI, FINISHED }
    private var currentState = State.GREETING

    private var bubbleText = ""
    private var rockyPosX = 20f
    private var velX = 0.5f
    private var bobOffset = 0f
    private var bobDirection = 1

    // Componentes de UI
    private val actionPanel = JPanel(GridLayout(4, 1, 2, 2)) // Panel para los botones de CI

    private val imgStand = loadImage("/icons/stand.png")
    private val imgWalk1 = loadImage("/icons/walkleft1.png")
    private val imgWalk2 = loadImage("/icons/walkleft2.png")
    private var frameCount = 0

    private fun loadImage(path: String): Image? =
        javaClass.getResource(path)?.let { ImageIcon(it).image }

    init {
        isOpaque = false
        layout = null // Usamos layout nulo para posicionar la burbuja a mano

        // Configurar panel de botones (invisible al inicio)
        actionPanel.isOpaque = false
        setupCIButtons()
        add(actionPanel)

        // Iniciar secuencia
        startRocky()

        // Timer de animación
        Timer(50) {
            if (currentState != State.ASKING_CI) {
                rockyPosX += velX
                if (rockyPosX > 100f || rockyPosX < 0f) velX *= -1
                bobOffset += bobDirection * 0.7f
                if (bobOffset > 5f || bobOffset < -5f) bobDirection *= -1
                frameCount++
            }
            repaint()
        }.start()

        // Listener para clics manuales
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                if (currentState == State.FINISHED) startRocky()
            }
        })
    }

    private fun startRocky() {
        currentState = State.GREETING
        bubbleText = "Hi, I'm Rocky! 🐶"
        actionPanel.isVisible = false

        // A los 3 segundos, pregunta por el CI
        Timer(3000) {
            askForCI()
        }.apply { isRepeats = false; start() }
    }

    private fun askForCI() {
        currentState = State.ASKING_CI
        bubbleText = "How do you want to implement CI?"

        // Posicionar el panel de botones justo encima de Rocky
        actionPanel.setBounds(10, 10, 180, 100)
        actionPanel.isVisible = true
        revalidate()
        repaint()
    }

    private fun setupCIButtons() {
        val options = listOf("Django", "Java with Maven", "Java with Gradle", "Node.js (Extra)")
        options.forEach { option ->
            val btn = JButton(option).apply {
                font = Font("SansSerif", Font.PLAIN, 10)
                margin = Insets(2, 5, 2, 5)
                isFocusable = false
                addActionListener {
                    selectCI(option)
                }
            }
            actionPanel.add(btn)
        }
    }

    private fun selectCI(option: String) {
        actionPanel.isVisible = false
        bubbleText = "Great! Let's setup $option..."
        currentState = State.FINISHED

        Timer(3000) {
            bubbleText = "Click me to restart"
            repaint()
        }.apply { isRepeats = false; start() }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val rX = rockyPosX.toInt()
        val rY = height - 90 + bobOffset.toInt()

        // Dibujar burbuja de fondo
        drawSpeechBubble(g2, rX + 40, rY)

        // Dibujar a Rocky
        val currentImg = if ((frameCount / 6) % 2 == 0) imgWalk1 else imgWalk2
        val img = if (currentState == State.ASKING_CI) imgStand else currentImg

        if (img != null) {
            if (velX > 0) g2.drawImage(img, rX + 80, rY, -80, 80, null)
            else g2.drawImage(img, rX, rY, 80, 80, null)
        }
    }

    private fun drawSpeechBubble(g2: Graphics2D, x: Int, y: Int) {
        val fm = g2.fontMetrics
        val padding = 12
        val bw = 200
        val bh = if (currentState == State.ASKING_CI) 130 else 40
        val bx = x - bw / 2
        val by = y - bh - 10

        // Sombra
        g2.color = Color(0, 0, 0, 30)
        g2.fillRoundRect(bx + 2, by + 2, bw, bh, 15, 15)

        // Cuerpo de la burbuja
        g2.color = Color.WHITE
        g2.fillRoundRect(bx, by, bw, bh, 15, 15)
        g2.color = Color(180, 180, 180)
        g2.drawRoundRect(bx, by, bw, bh, 15, 15)

        // Texto
        g2.color = Color.BLACK
        g2.font = Font("SansSerif", Font.BOLD, 11)
        g2.drawString(bubbleText, bx + 10, by + 20)

        // Si estamos preguntando, movemos el panel de botones aquí
        if (currentState == State.ASKING_CI) {
            actionPanel.setLocation(bx + 10, by + 30)
        }
    }
}