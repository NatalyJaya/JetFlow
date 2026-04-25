package com.github.natalyjaya.jetflo.cd

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Two-layer credential storage:
 *
 *  1. GLOBAL  — Render API Key stored in the OS keychain via IntelliJ PasswordSafe.
 *               Shared across all projects on the machine.
 *
 *  2. PER-PROJECT — The chosen serviceId written to `.idea/jetflo.xml` inside the
 *                   project directory. This means different projects can point to
 *                   different Render services with the same API key.
 */
object RenderCredentialsStore {

    private const val SUBSYSTEM = "JetFlo"
    private const val KEY_API   = "render_api_key"

    // ── Global: API Key (PasswordSafe / OS keychain) ──────────────────────────

    fun saveApiKey(apiKey: String) {
        PasswordSafe.instance.set(attrs(KEY_API), Credentials(KEY_API, apiKey))
    }

    fun getApiKey(): String? =
        PasswordSafe.instance.getPassword(attrs(KEY_API))

    fun clearApiKey() {
        PasswordSafe.instance.set(attrs(KEY_API), null)
    }

    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    // ── Per-project: Service ID (.idea/jetflo.xml) ────────────────────────────

    /**
     * Saves [serviceId] into `<projectRoot>/.idea/jetflo.xml`.
     * Creates the file if it doesn't exist.
     */
    fun saveServiceId(project: Project, serviceId: String) {
        val file = configFile(project)
        file.parentFile.mkdirs()
        file.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <jetflo>
                <serviceId>$serviceId</serviceId>
            </jetflo>
            """.trimIndent()
        )
    }

    /**
     * Reads the serviceId from `<projectRoot>/.idea/jetflo.xml`, or null if not set.
     */
    fun getServiceId(project: Project): String? {
        val file = configFile(project)
        if (!file.exists()) return null
        return try {
            val text  = file.readText()
            val start = text.indexOf("<serviceId>")
            val end   = text.indexOf("</serviceId>")
            if (start == -1 || end == -1) null
            else text.substring(start + "<serviceId>".length, end).trim().takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    fun clearServiceId(project: Project) {
        configFile(project).delete()
    }

    fun hasServiceId(project: Project): Boolean = !getServiceId(project).isNullOrBlank()

    // ── Convenience ───────────────────────────────────────────────────────────

    /** True when both global API key and project-scoped serviceId are present. */
    fun isConfigured(project: Project): Boolean = hasApiKey() && hasServiceId(project)

    /** Wipes everything: global key + project binding. */
    fun clearAll(project: Project) {
        clearApiKey()
        clearServiceId(project)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun configFile(project: Project): File {
        val base = project.basePath ?: System.getProperty("user.home")
        return File("$base/.idea/jetflo.xml")
    }

    private fun attrs(key: String) =
        CredentialAttributes(generateServiceName(SUBSYSTEM, key))
}