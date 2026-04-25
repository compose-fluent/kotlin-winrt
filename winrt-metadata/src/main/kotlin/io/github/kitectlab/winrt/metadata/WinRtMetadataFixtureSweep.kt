package io.github.kitectlab.winrt.metadata

data class WinRtMetadataFixtureSweepCase(
    val name: String,
    val context: WinRtMetadataProjectionContext,
    val expectResolvable: Boolean = true,
    val required: Boolean = expectResolvable,
    val validateProjectionInputs: Boolean = true,
    val expectedNamespaces: Set<String> = emptySet(),
    val expectedPackageAssetKinds: Set<WinRtPackageAssetKind> = emptySet(),
)

data class WinRtMetadataFixtureSweepResult(
    val name: String,
    val resolvedFiles: List<String>,
    val packageAssets: List<WinRtPackageAsset> = emptyList(),
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
            val report = WinRtMetadataDiagnosticReport(
                projectionDiagnostics(sweepCase, model, options) +
                    missingNamespaceDiagnostics(sweepCase, model) +
                    missingPackageAssetDiagnostics(sweepCase, cache),
            )
            if (sweepCase.expectResolvable) {
                WinRtMetadataFixtureSweepResult(
                    name = sweepCase.name,
                    resolvedFiles = cache.files.map { it.toString() },
                    packageAssets = cache.packageAssets,
                    diagnosticReport = report,
                )
            } else {
                WinRtMetadataFixtureSweepResult(
                    name = sweepCase.name,
                    resolvedFiles = cache.files.map { it.toString() },
                    packageAssets = cache.packageAssets,
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
                severity = if (sweepCase.required) WinRtMetadataDiagnosticSeverity.Error else WinRtMetadataDiagnosticSeverity.Warning,
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

private fun projectionDiagnostics(
    sweepCase: WinRtMetadataFixtureSweepCase,
    model: WinRtMetadataModel,
    options: WinRtMetadataValidationOptions,
): List<WinRtMetadataDiagnostic> =
    if (sweepCase.validateProjectionInputs) {
        model.validateForProjection(options).diagnostics
    } else {
        emptyList()
    }

private fun missingNamespaceDiagnostics(
    sweepCase: WinRtMetadataFixtureSweepCase,
    model: WinRtMetadataModel,
): List<WinRtMetadataDiagnostic> {
    val namespaces = model.namespaces.map(WinRtNamespace::name).toSet()
    return sweepCase.expectedNamespaces
        .filterNot { it in namespaces }
        .map { namespace ->
            WinRtMetadataDiagnostic(
                code = WinRtMetadataDiagnosticCode.MissingReferencedMetadata,
                severity = WinRtMetadataDiagnosticSeverity.Error,
                message = "Sweep case '${sweepCase.name}' did not load expected namespace '$namespace'.",
            )
        }
}

private fun missingPackageAssetDiagnostics(
    sweepCase: WinRtMetadataFixtureSweepCase,
    cache: WinRtMetadataCache,
): List<WinRtMetadataDiagnostic> {
    val kinds = cache.packageAssets.map(WinRtPackageAsset::kind).toSet()
    return sweepCase.expectedPackageAssetKinds
        .filterNot { it in kinds }
        .map { kind ->
            WinRtMetadataDiagnostic(
                code = WinRtMetadataDiagnosticCode.MissingReferencedMetadata,
                severity = WinRtMetadataDiagnosticSeverity.Error,
                message = "Sweep case '${sweepCase.name}' did not discover expected package asset kind '$kind'.",
            )
        }
}
