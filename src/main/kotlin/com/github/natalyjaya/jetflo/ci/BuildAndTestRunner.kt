package com.github.natalyjaya.jetflo.ci

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
