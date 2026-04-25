package com.github.natalyjaya.jetflo.ci

import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.ui.Messages

class CommitBuildCheckinHandlerFactory : CheckinHandlerFactory() {

    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
        return object : CheckinHandler() {
            override fun beforeCheckin(): ReturnResult {
                val project = panel.project
                val runner = BuildAndTestRunner(project)

                BuildStatusPanel.instance?.showRunning()

                val result = runner.runBlocking()

                BuildStatusPanel.instance?.showResult(result)

                return when {
                    result.success -> ReturnResult.COMMIT
                    else -> {
                        val message = buildString {
                            appendLine("⚠️ Build/tests failed. Commit blocked.\n")
                            result.failures.forEach { f ->
                                appendLine("• ${f.className}#${f.testName}")
                                appendLine("  ${f.message}")
                            }
                        }
                        val choice = Messages.showYesNoDialog(
                            project,
                            message,
                            "JetFlo – CI Failed",
                            "Commit Anyway",
                            "Cancel Commit",
                            Messages.getWarningIcon()
                        )
                        if (choice == Messages.YES) ReturnResult.COMMIT else ReturnResult.CANCEL
                    }
                }
            }
        }
    }
}