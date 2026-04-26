package com.github.natalyjaya.jetflo.ci

import com.github.natalyjaya.jetflo.auth.AuthManager
import com.github.natalyjaya.jetflo.ui.RockyWidget
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ide.BrowserUtil
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.swing.SwingUtilities

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

        Thread {
            Thread.sleep(5000)

            val newRunId = waitForNewRun(owner, repo, token)
            if (newRunId == null) {
                SwingUtilities.invokeLater {
                    notifyRocky("No CI run found\nafter push.")
                }
                return@Thread
            }

            pollUntilComplete(owner, repo, token, newRunId)
        }.apply { isDaemon = true; start() }
    }

    private fun waitForNewRun(owner: String, repo: String, token: String): Long? {
        repeat(10) {
            try {
                val runs = fetchRuns(owner, repo, token)
                if (runs.length() > 0) {
                    val latest = runs.getJSONObject(0)
                    val status = latest.getString("status")
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
                val status     = json.getString("status")
                val conclusion = json.optString("conclusion")
                val runUrl     = json.optString("html_url")

                when {
                    status != "completed" -> {
                        SwingUtilities.invokeLater { notifyRocky("⏳ CI running...") }
                        Thread.sleep(5000)
                        continue
                    }
                    conclusion == "success" -> {
                        SwingUtilities.invokeLater {
                            notifyRocky("Amaze! CI passed! \uD83D\uDC4E \uD83D\uDC4E \nGreat job!")
                            BuildStatusPanel.instance?.showResult(
                                BuildResult(true, emptyList(), "GitHub Actions: success")
                            )
                            // Notificación persistente de éxito
                            showPersistentNotification(
                                title   = "JetFlo CI – Passed",
                                content = "Build and tests passed successfully" ,
                                type    = NotificationType.INFORMATION,
                                runUrl  = runUrl
                            )
                        }
                        return
                    }
                    else -> {
                        val failedSteps = getFailedSteps(owner, repo, token, runId)
                        SwingUtilities.invokeLater {
                            notifyRocky("Bad very bad :( CI failed!\nCheck details.")
                            BuildStatusPanel.instance?.showResult(
                                BuildResult(false, listOf(
                                    TestFailure("GitHubActions", "workflow", conclusion, runUrl, null)
                                ), "GitHub Actions: $conclusion")
                            )
                            // Notificación persistente de fallo
                            val stepsText = if (failedSteps.isNotEmpty())
                                "<br>Failed steps:<br>" + failedSteps.joinToString("<br>") { "• $it" }
                            else ""
                            showPersistentNotification(
                                title   = "JetFlo CI – Failed",
                                content = "Workflow failed ($conclusion).$stepsText <a href=\"$runUrl\">View run</a>",
                                type    = NotificationType.ERROR,
                                runUrl  = runUrl
                            )
                            // Diálogo con detalle
                            showFailureDialog(conclusion, runUrl, failedSteps)
                        }
                        return
                    }
                }
            } catch (e: Exception) {
                Thread.sleep(5000)
            }
        }
    }

    private fun showPersistentNotification(
        title: String,
        content: String,
        type: NotificationType,
        runUrl: String
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("JetFlo CI")
            .createNotification(title, content, type)
            .addAction(com.intellij.notification.NotificationAction.createSimple("Open in Browser") {
                BrowserUtil.browse(runUrl)
            })
            .notify(project)
    }

    private fun getFailedSteps(owner: String, repo: String, token: String, runId: Long): List<String> {
        return try {
            val url = URL("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/jobs")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/vnd.github+json")

            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val jobs = json.getJSONArray("jobs")
            val failedSteps = mutableListOf<String>()

            for (i in 0 until jobs.length()) {
                val job = jobs.getJSONObject(i)
                val jobName       = job.getString("name")
                val jobConclusion = job.optString("conclusion")
                if (jobConclusion == "failure") {
                    val steps = job.getJSONArray("steps")
                    for (j in 0 until steps.length()) {
                        val step = steps.getJSONObject(j)
                        if (step.optString("conclusion") == "failure") {
                            failedSteps.add("[$jobName] ${step.getString("name")}")
                        }
                    }
                }
            }
            failedSteps
        } catch (e: Exception) { emptyList() }
    }

    private fun showFailureDialog(conclusion: String, runUrl: String, failedSteps: List<String>) {
        ApplicationManager.getApplication().invokeLater {
            val stepsDetail = if (failedSteps.isNotEmpty())
                "\n\nFailed steps:\n" + failedSteps.joinToString("\n") { "• $it" }
            else ""
            val message = "❌ GitHub Actions workflow failed ($conclusion).$stepsDetail\n\nView full logs:\n$runUrl"
            val choice = Messages.showYesNoDialog(
                project, message, "JetFlo – CI Failed",
                "Open in Browser", "Close", Messages.getErrorIcon()
            )
            if (choice == Messages.YES) BrowserUtil.browse(runUrl)
        }
    }

    private fun fetchRuns(owner: String, repo: String, token: String): org.json.JSONArray {
        val url = URL("https://api.github.com/repos/$owner/$repo/actions/runs?per_page=1")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        return JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("workflow_runs")
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
        val sshRegex   = Regex("git@github\\.com:([^/]+)/([^/.]+)")
        httpsRegex.find(remoteUrl)?.let { return Pair(it.groupValues[1], it.groupValues[2]) }
        sshRegex.find(remoteUrl)?.let   { return Pair(it.groupValues[1], it.groupValues[2]) }
        return null
    }

    private fun notifyRocky(message: String) {
        RockyWidget.instance?.showMessage(message)
    }
}