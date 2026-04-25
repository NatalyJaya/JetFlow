
package com.github.natalyjaya.jetflo.ci

import com.github.natalyjaya.jetflo.ui.RockyWidget
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.application.ApplicationManager
import git4idea.GitVcs

class CommitBuildCheckinHandlerFactory : VcsCheckinHandlerFactory(GitVcs.getKey()) {

    override fun createVcsHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
        return object : CheckinHandler() {
            override fun beforeCheckin(): ReturnResult {
                val project = panel.project
                val runner = BuildAndTestRunner(project)

                BuildStatusPanel.instance?.showRunning()

                var result: BuildResult? = null
                ProgressManager.getInstance().runProcessWithProgressSynchronously(
                    { result = runner.runBlocking() },
                    "JetFlo – Running CI…",
                    false,
                    project
                )

                val buildResult = result ?: BuildResult(false, emptyList(), "Build did not run")
                //BuildStatusPanel.instance?.showResult(buildResult)

                return if (buildResult.success) {
                    // Rocky avisa del éxito
                    ReturnResult.COMMIT
                } else {

                    val message = buildString {
                        appendLine("⚠️ Build/tests failed. Commit blocked.\n")
                        buildResult.failures.forEach { f ->
                            appendLine("• ${f.className}#${f.testName}")
                            appendLine("  ${f.message}")
                        }
                    }
                    ReturnResult.COMMIT
                }
            }
        }
    }
}
