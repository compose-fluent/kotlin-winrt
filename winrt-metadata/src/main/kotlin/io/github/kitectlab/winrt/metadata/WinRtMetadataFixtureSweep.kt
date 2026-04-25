package io.github.kitectlab.winrt.metadata

data class WinRtMetadataFixtureSweepCase(
    val name: String,
    val context: WinRtMetadataProjectionContext,
    val expectResolvable: Boolean = true,
)

data class WinRtMetadataFixtureSweepResult(
    val name: String,
    val resolvedFiles: List<String>,
    val diagnosticReport: WinRtMetadataDiagnosticReport,
) {
    val passed: Boolean
        get() = !diagnosticReport.hasErrors
}

data class WinRtMetadataFixtureSweepReport(
    val results: List<WinRtMetadataFixtureSweepResult>,
) {
    val failed: List<WinRtMetadataFixtureSweepResult>
        get() = results.filterNot(WinRtMetadataFixtureSweepResult::passed)

    val passed: List<WinRtMetadataFixtureSweepResult>
        get() = results.filter(WinRtMetadataFixtureSweepResult::passed)

    val isGreen: Boolean
        get() = failed.isEmpty()

    fun throwIfFailed() {
        if (!isGreen) {
            val diagnostics = failed.flatMap { result ->
                result.diagnosticReport.diagnostics.map { diagnostic ->
                    diagnostic.copy(
                        message = "[${result.name}] ${diagnostic.message}",
                    )
                }
            }
            throw WinRtMetadataDiagnosticException(WinRtMetadataDiagnosticReport(diagnostics))
        }
    }
}

object WinRtMetadataFixtureSweep {
    fun run(
        cases: List<WinRtMetadataFixtureSweepCase>,
        options: WinRtMetadataValidationOptions = WinRtMetadataValidationOptions(),
    ): WinRtMetadataFixtureSweepReport =
        WinRtMetadataFixtureSweepReport(
            cases.map { sweepCase -> runCase(sweepCase, options) },
        )

    private fun runCase(
        sweepCase: WinRtMetadataFixtureSweepCase,
        options: WinRtMetadataValidationOptions,
    ): WinRtMetadataFixtureSweepResult =
        try {
            val cache = sweepCase.context.resolveCache()
            val model = cache.load()
            val report = model.validateForProjection(options)
            if (sweepCase.expectResolvable) {
                WinRtMetadataFixtureSweepResult(
                    name = sweepCase.name,
                    resolvedFiles = cache.files.map { it.toString() },
                    diagnosticReport = report,
                )
            } else {
                WinRtMetadataFixtureSweepResult(
                    name = sweepCase.name,
                    resolvedFiles = cache.files.map { it.toString() },
                    diagnosticReport = WinRtMetadataDiagnosticReport(
                        report.diagnostics + WinRtMetadataDiagnostic(
                            code = WinRtMetadataDiagnosticCode.InvalidCommandSpecification,
                            severity = WinRtMetadataDiagnosticSeverity.Error,
                            message = "Sweep case '${sweepCase.name}' was expected to be unresolved but resolved successfully.",
                        ),
                    ),
                )
            }
        } catch (error: IllegalArgumentException) {
            val diagnostic = WinRtMetadataDiagnostic(
                code = diagnosticCodeForSweepFailure(error.message.orEmpty()),
                severity = if (sweepCase.expectResolvable) WinRtMetadataDiagnosticSeverity.Error else WinRtMetadataDiagnosticSeverity.Warning,
                message = error.message ?: "Metadata fixture sweep case '${sweepCase.name}' failed.",
            )
            WinRtMetadataFixtureSweepResult(
                name = sweepCase.name,
                resolvedFiles = emptyList(),
                diagnosticReport = WinRtMetadataDiagnosticReport(listOf(diagnostic)),
            )
        }
}

private fun diagnosticCodeForSweepFailure(message: String): WinRtMetadataDiagnosticCode =
    when {
        "missing WinMD" in message || "references missing" in message -> WinRtMetadataDiagnosticCode.MissingReferencedMetadata
        "Response file" in message || "ApiContract" in message -> WinRtMetadataDiagnosticCode.InvalidCommandSpecification
        else -> WinRtMetadataDiagnosticCode.InvalidMetadataSource
    }
