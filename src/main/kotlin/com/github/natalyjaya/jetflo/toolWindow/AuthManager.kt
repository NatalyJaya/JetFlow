package com.github.natalyjaya.jetflo.toolWindow

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe

object AuthManager {
    // Usamos un String directo y único en lugar de usar el 'Defaults'
    private val credentialAttributes = CredentialAttributes("JetFlo_GitHub_PAT")

    fun saveToken(token: String) {
        val credentials = Credentials("github_user", token)
        PasswordSafe.instance.set(credentialAttributes, credentials)
    }

    fun getToken(): String? {
        return PasswordSafe.instance.getPassword(credentialAttributes)
    }
}