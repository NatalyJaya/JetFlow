package com.github.natalyjaya.jetflo.ci

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import git4idea.push.GitPushListener
import git4idea.push.GitPushRepoResult
import git4idea.repo.GitRepository

class JetFloPushStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.messageBus.connect().subscribe(
            GitPushListener.TOPIC,
            object : GitPushListener {
                override fun onCompleted(repository: GitRepository, pushResult: GitPushRepoResult) {
                    GitHubActionsPoller(project).startPolling()
                }
            }
        )
    }
}