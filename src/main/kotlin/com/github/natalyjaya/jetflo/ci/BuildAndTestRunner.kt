package com.github.natalyjaya.jetflo.ci

import com.intellij.openapi.project.Project
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

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
        val basePath = project.basePath ?: return BuildResult(false, emptyList(), "No project path")
        val gradlew = if (System.getProperty("os.name").lowercase().contains("win"))
            "$basePath/gradlew.bat" else "$basePath/gradlew"

        val process = ProcessBuilder(gradlew, "test", "--rerun-tasks")
            .directory(File(basePath))
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()

        val failures = parseTestReports(basePath)
        val success = failures.isEmpty()

        TestFailureStore.update(failures)

        return BuildResult(success, failures, output)
    }

    private fun parseTestReports(basePath: String): List<TestFailure> {
        val reportsDir = File("$basePath/build/reports/tests/test/xml")
            .takeIf { it.exists() }
            ?: File("$basePath/build/test-results/test")
                .takeIf { it.exists() }
            ?: return emptyList()

        val dbf = DocumentBuilderFactory.newInstance()
        val failures = mutableListOf<TestFailure>()

        reportsDir.walkTopDown()
            .filter { it.extension == "xml" }
            .forEach { xmlFile ->
                runCatching {
                    val doc = dbf.newDocumentBuilder().parse(xmlFile)
                    val testCases = doc.getElementsByTagName("testcase")
                    for (i in 0 until testCases.length) {
                        val tc = testCases.item(i) as Element
                        val failureNodes = tc.getElementsByTagName("failure")
                        if (failureNodes.length > 0) {
                            val failEl = failureNodes.item(0) as Element
                            val trace = failEl.textContent
                            failures += TestFailure(
                                className = tc.getAttribute("classname"),
                                testName  = tc.getAttribute("name"),
                                message   = failEl.getAttribute("message").ifBlank { trace.lines().firstOrNull() ?: "" },
                                stackTrace = trace,
                                lineNumber = extractLineNumber(trace, tc.getAttribute("classname"))
                            )
                        }
                    }
                }
            }

        return failures
    }

    private fun extractLineNumber(stackTrace: String, className: String): Int? {
        val simpleClass = className.substringAfterLast('.')
        val regex = Regex("""\($simpleClass\.kt:(\d+)\)""")
        return regex.find(stackTrace)?.groupValues?.get(1)?.toIntOrNull()
    }
}