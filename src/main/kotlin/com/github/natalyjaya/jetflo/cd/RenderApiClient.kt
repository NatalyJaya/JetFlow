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
    //  Owner — required for service creation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the ownerId (user or team) associated with [apiKey].
     * Render requires this field when creating a new service.
     *
     * Calls GET /user and falls back to GET /owners if needed.
     */
    fun getOwnerId(apiKey: String): String {
        // GET /v1/owners is the correct "List workspaces" endpoint.
        // GET /v1/users returns { "email", "name" } with NO id field — unusable.
        // Response shape: [ { "owner": { "id": "own-xxxx", "name": "...", ... } }, ... ]
        val resp = get("$BASE/owners?limit=1", apiKey)
        val arr  = JSONArray(resp)
        if (arr.length() == 0) error("No workspaces found for this API key")
        val first = arr.getJSONObject(0)
        return if (first.has("owner")) {
            first.getJSONObject("owner").getString("id")
        } else {
            first.getString("id")
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
        plan: String = "free",
        env: String = "node"
    ): String {
        // FIX: ownerId is required by the Render API — fetch it first
        val ownerId = getOwnerId(apiKey)

        val body = JSONObject().apply {
            put("type", "web_service")
            put("name", name)
            put("ownerId", ownerId)
            put("autoDeploy", "yes")
            put("repo", repoUrl)
            put("branch", branch)
            put("serviceDetails", JSONObject().apply {
                put("env", env)
                put("plan", plan)
                put("region", "frankfurt")
                put("numInstances", 1)
                put("pullRequestPreviewsEnabled", "no")
                // Render requires buildCommand+startCommand inside envSpecificDetails
                // for all non-docker, non-static runtimes
                put("envSpecificDetails", JSONObject().apply {
                    put("buildCommand", buildCmd.ifBlank { "echo 'no build step'" })
                    put("startCommand", startCmd.ifBlank { defaultStartCommand(env) })
                })
            })
        }.toString()

        val resp = post("$BASE/services", apiKey, body)
        // Guard: if response starts with '[' it's not a JSON object
        if (resp.trimStart().startsWith("[")) {
            error("createService unexpected response: $resp")
        }
        val respObj = JSONObject(resp)
        // Render wraps the result: { "service": { "id": "..." }, "deployId": "..." }
        return if (respObj.has("service")) respObj.getJSONObject("service").getString("id")
        else respObj.getString("id")
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
        val resp = post("$BASE/services/$serviceId/deploys", apiKey, body).trim()

        // Render sometimes returns 201 with an empty body — deploy was still triggered.
        // Fall back to fetching the latest deploy id via the list endpoint.
        if (resp.isEmpty()) {
            val latest = getLatestDeploy(apiKey, serviceId)
            return latest?.first ?: serviceId  // serviceId as last-resort sentinel
        }

        return try {
            when {
                resp.startsWith("[") -> {
                    val arr = org.json.JSONArray(resp)
                    val obj = arr.getJSONObject(0)
                    if (obj.has("deploy")) obj.getJSONObject("deploy").getString("id")
                    else obj.getString("id")
                }
                else -> {
                    val obj = JSONObject(resp)
                    if (obj.has("deploy")) obj.getJSONObject("deploy").getString("id")
                    else obj.getString("id")
                }
            }
        } catch (e: Exception) {
            error("triggerDeploy parse failed.\nRaw response was:\n\n$resp\n\nParse error: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Deploy monitoring — Polling (Forma A monitor + Forma B monitor)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the current status of a single deploy.
     */
    fun getDeployStatus(apiKey: String, serviceId: String, deployId: String): DeployStatus {
        val resp   = get("$BASE/services/$serviceId/deploys/$deployId", apiKey)
        val obj    = JSONObject(resp)
        val deploy = if (obj.has("deploy")) obj.getJSONObject("deploy") else obj
        return DeployStatus.from(deploy.optString("status", "unknown"))
    }

    fun getLatestDeploy(apiKey: String, serviceId: String): Pair<String, DeployStatus>? {
        val resp = get("$BASE/services/$serviceId/deploys?limit=1", apiKey)
        val arr  = JSONArray(resp)
        if (arr.length() == 0) return null
        val raw  = arr.getJSONObject(0)
        val obj  = if (raw.has("deploy")) raw.getJSONObject("deploy") else raw
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
        val resp   = get("$BASE/services/$serviceId/deploys?limit=20", apiKey)
        val arr    = JSONArray(resp)
        val target = (0 until arr.length())
            .map { arr.getJSONObject(it) }
            .firstOrNull { obj ->
                obj.getString("id") != failedDeployId &&
                        DeployStatus.from(obj.optString("status", "")) == DeployStatus.LIVE
            } ?: return null

        val targetDeployId = target.getString("id")

        val rollbackResp = post(
            "$BASE/services/$serviceId/deploys/$targetDeployId/rollback",
            apiKey,
            "{}"
        )
        return JSONObject(rollbackResp).getString("id")
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun defaultStartCommand(env: String) = when (env) {
        "node"   -> "node index.js"
        "python" -> "gunicorn app:app"
        "ruby"   -> "bundle exec ruby app.rb"
        "go"     -> "./main"
        "rust"   -> "./target/release/app"
        "elixir" -> "mix run --no-halt"
        else     -> "echo 'set your start command'"
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
        return conn.readResponseOrThrow()
    }

    private fun post(url: String, apiKey: String, body: String): String {
        // Do NOT access outputStream inside apply{} — it triggers the connection
        // immediately and HttpURLConnection throws a generic IOException on 4xx
        // before readResponseOrThrow can intercept and read the real error body.
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 10_000
        conn.readTimeout    = 15_000
        conn.doOutput       = true
        conn.outputStream.bufferedWriter().use { it.write(body) }
        return conn.readResponseOrThrow()
    }

    /**
     * FIX: Always read from errorStream on 4xx/5xx so the actual API error
     * message (e.g. "ownerId is required") is surfaced instead of a generic
     * IOException("Server returned HTTP response code: 400").
     */
    private fun HttpURLConnection.readResponseOrThrow(): String {
        val code    = responseCode          // finalises the request
        val isError = code >= 400
        val text    = (if (isError) errorStream else inputStream)
            ?.bufferedReader()?.readText() ?: ""
        if (isError) error("Render API error $code: $text")
        return text
    }
}