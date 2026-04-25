package com.github.natalyjaya.jetflo.ci

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

object TestFailureStore {
    @Volatile var lastFailures: List<TestFailure> = emptyList()

    fun update(failures: List<TestFailure>) {
        lastFailures = failures
    }

    fun clear() {
        lastFailures = emptyList()
    }
}

class TestFailureAnnotator : ExternalAnnotator<List<TestFailure>, List<TestFailure>>() {

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): List<TestFailure> {
        val virtualPath = file.virtualFile?.path ?: return emptyList()
        return TestFailureStore.lastFailures.filter { failure ->
            val simpleClass = failure.className.substringAfterLast('.')
            virtualPath.endsWith("$simpleClass.kt") || virtualPath.endsWith("$simpleClass.java")
        }
    }

    override fun doAnnotate(collectedInfo: List<TestFailure>): List<TestFailure> = collectedInfo

    override fun apply(file: PsiFile, annotationResult: List<TestFailure>, holder: AnnotationHolder) {
        if (annotationResult.isEmpty()) return

        val document = file.viewProvider.document ?: return

        annotationResult.forEach { failure ->
            val line0 = (failure.lineNumber?.minus(1))?.coerceIn(0, document.lineCount - 1)
                ?: (document.lineCount - 1)

            val lineStart = document.getLineStartOffset(line0)
            val lineEnd   = document.getLineEndOffset(line0)
            val range     = TextRange(lineStart, lineEnd)

            holder.newAnnotation(HighlightSeverity.ERROR, "❌ Test failed: ${failure.testName}\n${failure.message}")
                .range(range)
                .needsUpdateOnTyping(false)
                .create()
        }
    }
}