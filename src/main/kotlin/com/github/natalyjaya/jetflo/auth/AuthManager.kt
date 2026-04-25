package com.github.natalyjaya.jetflo.auth

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

object AuthManager {
    private const val SUBSYSTEM = "JetFlo"
    private const val TOKEN_KEY = "GITHUB_PAT"

    private fun createAttributes(): CredentialAttributes {
        return CredentialAttributes(generateServiceName(SUBSYSTEM, TOKEN_KEY))
    }

    fun saveToken(token: String) {
        PasswordSafe.instance.set(createAttributes(), com.intellij.credentialStore.Credentials(null, token))
    }

    fun getToken(): String? {
        return PasswordSafe.instance.getPassword(createAttributes())
    }
}