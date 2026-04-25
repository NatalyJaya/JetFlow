package com.github.natalyjaya.jetflo.ci

import com.github.natalyjaya.jetflo.auth.AuthManager
import com.github.natalyjaya.jetflo.ui.RockyWidget
import com.intellij.openapi.project.Project
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.swing.SwingUtilities
import javax.swing.Timer

class GitHubActionsPoller(private val project: Project) {

    fun startPolling() {
        val (owner, repo) = getOwnerAndRepo() ?: run {
            notifyRocky("Could not read\ngit remote!")
            return
        }
        val token = AuthManager.getToken() ?: run {
            notifyRocky("No GitHub token!\nSet it in JetFlo panel.")
            return
        }

        notifyRocky("Push detected!\nChecking CI...")
        BuildStatusPanel.instance?.showRunning()

        // Esperamos 5 segundos para que GitHub registre el nuevo run
        Thread {
            Thread.sleep(5000)

            // Guardamos el run id del último run antes del push para
            // detectar cuándo aparece uno nuevo
            val newRunId = waitForNewRun(owner, repo, token)
            if (newRunId == null) {
                SwingUtilities.invokeLater {
                    notifyRocky("No CI run found\nafter push.")
                }
                return@Thread
            }

            // Polling hasta que el run termine
            pollUntilComplete(owner, repo, token, newRunId)
        }.apply { isDaemon = true; start() }
    }

    private fun waitForNewRun(owner: String, repo: String, token: String): Long? {
        // Intentamos hasta 10 veces con 3 segundos de espera encontrar un run reciente
        repeat(10) {
            try {
                val runs = fetchRuns(owner, repo, token)
                if (runs.length() > 0) {
                    val latest = runs.getJSONObject(0)
                    val status = latest.getString("status")
                    // Si está en cola o corriendo, es el nuevo
                    if (status == "queued" || status == "in_progress") {
                        return latest.getLong("id")
                    }
                }
            } catch (_: Exception) {}
            Thread.sleep(3000)
        }
        return null
    }

    private fun pollUntilComplete(owner: String, repo: String, token: String, runId: Long) {
        while (true) {
            try {
                val url = URL("https://api.github.com/repos/$owner/$repo/actions/runs/$runId")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Accept", "application/vnd.github+json")

                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val status = json.getString("status")
                val conclusion = json.optString("conclusion")
                val runUrl = json.optString("html_url")

                when {
                    status != "completed" -> {
                        SwingUtilities.invokeLater { notifyRocky("⏳ CI running...") }
                        Thread.sleep(5000)
                        continue
                    }
                    conclusion == "success" -> {
                        SwingUtilities.invokeLater {
                            notifyRocky("✅ CI passed!\nGreat job!")
                            BuildStatusPanel.instance?.showResult(
                                BuildResult(true, emptyList(), "GitHub Actions: success")
                            )
                        }
                        return
                    }
                    else -> {
                        SwingUtilities.invokeLater {
                            notifyRocky("❌ CI failed!\n$conclusion")
                            BuildStatusPanel.instance?.showResult(
                                BuildResult(false, listOf(
                                    TestFailure("GitHubActions", "workflow", conclusion, runUrl, null)
                                ), "GitHub Actions: $conclusion")
                            )
                        }
                        return
                    }
                }
            } catch (e: Exception) {
                Thread.sleep(5000)
            }
        }
    }

    private fun fetchRuns(owner: String, repo: String, token: String): org.json.JSONArray {
        val url = URL("https://api.github.com/repos/$owner/$repo/actions/runs?per_page=1")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        val json = JSONObject(conn.inputStream.bufferedReader().readText())
        return json.getJSONArray("workflow_runs")
    }

    private fun getOwnerAndRepo(): Pair<String, String>? {
        val basePath = project.basePath ?: return null
        return try {
            val process = ProcessBuilder("git", "remote", "get-url", "origin")
                .directory(File(basePath))
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            parseGitRemote(output)
        } catch (e: Exception) { null }
    }

    private fun parseGitRemote(remoteUrl: String): Pair<String, String>? {
        val httpsRegex = Regex("https://github\\.com/([^/]+)/([^/.]+)")
        val sshRegex = Regex("git@github\\.com:([^/]+)/([^/.]+)")
        httpsRegex.find(remoteUrl)?.let { return Pair(it.groupValues[1], it.groupValues[2]) }
        sshRegex.find(remoteUrl)?.let { return Pair(it.groupValues[1], it.groupValues[2]) }
        return null
    }

    private fun notifyRocky(message: String) {
        RockyWidget.instance?.showMessage(message)
    }
}