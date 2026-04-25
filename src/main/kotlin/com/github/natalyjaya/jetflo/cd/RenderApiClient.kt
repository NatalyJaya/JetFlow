package com.github.natalyjaya.jetflo.cd

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Render REST API v1 client.
 * All methods are blocking — always call from a background thread.
 *
 * Docs: https://render.com/docs/api
 */
object RenderApiClient {

    private const val BASE = "https://api.render.com/v1"

    // ─────────────────────────────────────────────────────────────────────────
    //  Data classes
    // ─────────────────────────────────────────────────────────────────────────

    data class RenderService(val id: String, val name: String, val url: String?)

    /**
     * Deploy lifecycle states returned by Render.
     * https://render.com/docs/api#tag/deploys/GET/services/{serviceId}/deploys/{deployId}
     */
    enum class DeployStatus {
        CREATED, BUILD_IN_PROGRESS, UPDATE_IN_PROGRESS,
        LIVE, DEACTIVATED, BUILD_FAILED, UPDATE_FAILED,
        CANCELED, PRE_DEPLOY_IN_PROGRESS, PRE_DEPLOY_FAILED,
        UNKNOWN;

        val isTerminal get() = this in setOf(
            LIVE, DEACTIVATED, BUILD_FAILED, UPDATE_FAILED, CANCELED, PRE_DEPLOY_FAILED
        )
        val isSuccess get() = this == LIVE
        val isFailed  get() = this in setOf(BUILD_FAILED, UPDATE_FAILED, PRE_DEPLOY_FAILED)

        companion object {
            fun from(raw: String) = values().firstOrNull {
                it.name.equals(raw.replace("-", "_"), ignoreCase = true)
            } ?: UNKNOWN
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Phase A — List existing services
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all web services visible to [apiKey], sorted by name.
     */
    fun listServices(apiKey: String): List<RenderService> {
        val json = get("$BASE/services?limit=100&type=web_service", apiKey)
        val arr  = JSONArray(json)
        return (0 until arr.length())
            .map { arr.getJSONObject(it) }
            .map { obj ->
                val svc = if (obj.has("service")) obj.getJSONObject("service") else obj
                RenderService(
                    id   = svc.getString("id"),
                    name = svc.getString("name"),
                    url  = svc.optString("serviceDetails")
                        .takeIf { it.isNotBlank() }
                )
            }
            .sortedBy { it.name }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Phase B — Create a NEW service from scratch (Caso 1)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new Render web service linked to a GitHub repo.
     *
     * @param name        Display name for the service
     * @param repoUrl     Full GitHub HTTPS clone URL (e.g. https://github.com/user/repo)
     * @param branch      Branch to deploy from (default "main")
     * @param buildCmd    Build command (e.g. "./gradlew build")
     * @param startCmd    Start command (e.g. "java -jar build/libs/app.jar")
     * @param plan        Render plan: "free", "starter", "standard", etc.
     * @return The serviceId of the newly created service
     */
    fun createService(
        apiKey: String,
        name: String,
        repoUrl: String,
        branch: String = "main",
        buildCmd: String = "",
        startCmd: String = "",
        plan: String = "free"
    ): String {
        val body = JSONObject().apply {
            put("type", "web_service")
            put("name", name)
            put("autoDeploy", "yes")
            put("serviceDetails", JSONObject().apply {
                put("env", "docker")          // Render picks runtime from repo if omitted
                put("plan", plan)
                put("region", "frankfurt")
                put("numInstances", 1)
                if (buildCmd.isNotBlank()) put("buildCommand", buildCmd)
                if (startCmd.isNotBlank()) put("startCommand", startCmd)
                put("pullRequestPreviewsEnabled", "no")
            })
            put("repo", repoUrl)
            put("branch", branch)
        }.toString()

        val resp = post("$BASE/services", apiKey, body)
        return JSONObject(resp).getString("id")
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Deploy — Force deploy (Forma B)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Triggers a new deploy for [serviceId], regardless of new commits.
     * Returns the new deployId.
     */
    fun triggerDeploy(apiKey: String, serviceId: String): String {
        val body = JSONObject().put("clearCache", "do_not_clear").toString()
        val resp = post("$BASE/services/$serviceId/deploys", apiKey, body)
        return JSONObject(resp).getString("id")
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Deploy monitoring — Polling (Forma A monitor + Forma B monitor)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the current status of a single deploy.
     */
    fun getDeployStatus(apiKey: String, serviceId: String, deployId: String): DeployStatus {
        val resp = get("$BASE/services/$serviceId/deploys/$deployId", apiKey)
        return DeployStatus.from(JSONObject(resp).optString("status", "unknown"))
    }

    /**
     * Returns the most recent deploy for [serviceId], or null if none.
     * Useful for monitoring a deploy triggered by a git push (Forma A).
     */
    fun getLatestDeploy(apiKey: String, serviceId: String): Pair<String, DeployStatus>? {
        val resp = get("$BASE/services/$serviceId/deploys?limit=1", apiKey)
        val arr  = JSONArray(resp)
        if (arr.length() == 0) return null
        val obj  = arr.getJSONObject(0)
        return obj.getString("id") to DeployStatus.from(obj.optString("status", "unknown"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Rollback — via dedicated Render endpoint
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Rolls back [serviceId] to the last LIVE deploy before [failedDeployId].
     *
     * Uses the official Render rollback endpoint:
     *   POST /services/{serviceId}/deploys/{deployId}/rollback
     *
     * @return The new rollback deployId, or null if no suitable previous deploy exists.
     */
    fun rollback(apiKey: String, serviceId: String, failedDeployId: String): String? {
        // Find the last successful deploy (LIVE) that isn't the failed one
        val resp   = get("$BASE/services/$serviceId/deploys?limit=20", apiKey)
        val arr    = JSONArray(resp)
        val target = (0 until arr.length())
            .map { arr.getJSONObject(it) }
            .firstOrNull { obj ->
                obj.getString("id") != failedDeployId &&
                        DeployStatus.from(obj.optString("status", "")) == DeployStatus.LIVE
            } ?: return null

        val targetDeployId = target.getString("id")

        // Official rollback endpoint — redeploys that exact image/commit
        val rollbackResp = post(
            "$BASE/services/$serviceId/deploys/$targetDeployId/rollback",
            apiKey,
            "{}"
        )
        return JSONObject(rollbackResp).getString("id")
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HTTP helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun get(url: String, apiKey: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 10_000
            readTimeout    = 15_000
        }
        return conn.inputStream.bufferedReader().readText()
    }

    private fun post(url: String, apiKey: String, body: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 10_000
            readTimeout    = 15_000
            doOutput = true
            outputStream.bufferedWriter().use { it.write(body) }
        }
        return conn.inputStream.bufferedReader().readText()
    }
}