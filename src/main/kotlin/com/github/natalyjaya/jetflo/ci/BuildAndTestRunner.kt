package com.github.natalyjaya.jetflo.ci

import com.github.natalyjaya.jetflo.auth.AuthManager
import com.intellij.openapi.project.Project
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class TestFailure(
    val className: String,
    val testName: String,
    val message: String,
    val stackTrace: String,
    val lineNumber: Int?
)

data class BuildResult(
    val success: Boolean,
    val failures: List<TestFailure>,
    val rawOutput: String
)

class BuildAndTestRunner(private val project: Project) {

    fun runBlocking(): BuildResult {
        return try {
            val (owner, repo) = getOwnerAndRepo() ?: return BuildResult(
                false, emptyList(), "No se pudo obtener el repositorio de Git"
            )

            val token = AuthManager.getToken() ?: return BuildResult(
                false, emptyList(), "No hay token de GitHub configurado. Configúralo en el panel JetFlo."
            )

            val result = getLatestWorkflowRun(owner, repo, token)
            result
        } catch (e: Exception) {
            BuildResult(false, emptyList(), "Error consultando GitHub Actions: ${e.message}")
        }
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
        } catch (e: Exception) {
            null
        }
    }

    private fun parseGitRemote(remoteUrl: String): Pair<String, String>? {
        // Soporta tanto HTTPS como SSH:
        // https://github.com/owner/repo.git
        // git@github.com:owner/repo.git
        val httpsRegex = Regex("https://github\\.com/([^/]+)/([^/.]+)")
        val sshRegex = Regex("git@github\\.com:([^/]+)/([^/.]+)")

        httpsRegex.find(remoteUrl)?.let {
            return Pair(it.groupValues[1], it.groupValues[2])
        }
        sshRegex.find(remoteUrl)?.let {
            return Pair(it.groupValues[1], it.groupValues[2])
        }
        return null
    }

    private fun getLatestWorkflowRun(owner: String, repo: String, token: String): BuildResult {
        val url = URL("https://api.github.com/repos/$owner/$repo/actions/runs?per_page=1&branch=main")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")

        val response = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(response)
        val runs = json.getJSONArray("workflow_runs")

        if (runs.length() == 0) {
            // No hay runs todavía, dejamos commitear
            return BuildResult(true, emptyList(), "No hay workflow runs todavía")
        }

        val latestRun = runs.getJSONObject(0)
        val status = latestRun.getString("status")       // queued, in_progress, completed
        val conclusion = latestRun.optString("conclusion") // success, failure, cancelled...
        val runUrl = latestRun.optString("html_url")

        return when {
            status != "completed" -> {
                // El workflow está corriendo, avisamos pero dejamos commitear
                BuildResult(true, emptyList(), "Workflow en progreso: $status")
            }
            conclusion == "success" -> {
                BuildResult(true, emptyList(), "✅ GitHub Actions: success")
            }
            else -> {
                BuildResult(
                    false,
                    listOf(TestFailure(
                        className = "GitHubActions",
                        testName = "workflow_run",
                        message = "El último workflow falló ($conclusion). Ver: $runUrl",
                        stackTrace = "",
                        lineNumber = null
                    )),
                    "❌ GitHub Actions: $conclusion"
                )
            }
        }
    }
}