package com.github.natalyjaya.jetflo.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.WindowManager
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
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
                val w = 200
                val h = 160
                // CAMBIO: Posición X = 10 (Izquierda)
                rockyWidget.setBounds(10, fh - h - 40, w, h)
            }

            reposition()
            layeredPane.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) = reposition()
            })

            layeredPane.revalidate()
            layeredPane.repaint()
        }
    }
}

class RockyWidget : JPanel() {
    private val greetings = listOf(
        "Hey! Let's code!😂",
        "You got this!💻",
        "Hello, developer!🛠️",
        "Ready to build?🌟"
    )

    private var isTalking = false
    private var bubbleText: String? = null
    private var rockyPosX = 50f
    private var velX = 0.6f
    private var bobOffset = 0f
    private var bobDirection = 1
    private var frameCount = 0
    private var mainTimer: Timer? = null

    private val imgStand = loadImage("/icons/stand.png")
    private val imgWalk1 = loadImage("/icons/walkleft1.png")
    private val imgWalk2 = loadImage("/icons/walkleft2.png")

    private fun loadImage(path: String): Image? =
        javaClass.getResource(path)?.let { ImageIcon(it).image }

    init {
        isOpaque = false
        layout = null

        // --- NUEVA LÓGICA DE CLIC ---
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                // Si no está hablando ya, que diga algo al recibir clic
                if (!isTalking) {
                    showGreeting()
                }
            }
        })

        // Timer de animación y movimiento
        mainTimer = Timer(50) {
            if (!isTalking) {
                rockyPosX += velX
                if (rockyPosX > 120f || rockyPosX < 0f) {
                    velX *= -1
                }
                bobOffset += bobDirection * 0.7f
                if (bobOffset > 5f || bobOffset < -5f) bobDirection *= -1
                frameCount++
            }
            repaint()
        }
        mainTimer?.start()
    }

    fun showGreeting() {
        isTalking = true
        bubbleText = greetings.random()
        repaint()

        // La burbuja desaparece tras 3.5 segundos
        Timer(3500) {
            isTalking = false
            bubbleText = null
            repaint()
        }.apply { isRepeats = false; start() }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val rockyW = 80
        val rockyH = 80
        val rX = rockyPosX.toInt()
        val rY = (height - rockyH - 10 + bobOffset).toInt()

        bubbleText?.let { drawBubble(g2, it, rX + 40, rY) }

        val currentImg = when {
            isTalking -> imgStand
            (frameCount / 6) % 2 == 0 -> imgWalk1
            else -> imgWalk2
        } ?: imgStand

        if (currentImg != null) {
            // Espejo: si velX > 0 (derecha) invertimos imagen, si no (izquierda) normal
            if (velX > 0) {
                g2.drawImage(currentImg, rX + rockyW, rY, -rockyW, rockyH, null)
            } else {
                g2.drawImage(currentImg, rX, rY, rockyW, rockyH, null)
            }
        }
    }

    private fun drawBubble(g2: Graphics2D, text: String, x: Int, y: Int) {
        val fm = g2.fontMetrics
        val padding = 10
        val bw = fm.stringWidth(text) + padding * 2
        val bh = fm.height + padding
        val bx = x - bw / 2
        val by = y - bh - 15

        g2.color = Color(255, 255, 255, 245)
        g2.fillRoundRect(bx, by, bw, bh, 12, 12)
        g2.color = Color.DARK_GRAY
        g2.drawRoundRect(bx, by, bw, bh, 12, 12)
        g2.color = Color.BLACK
        g2.drawString(text, bx + padding, by + fm.ascent + (padding / 4))
    }
}