package io.github.composefluent.winrt.metadata

data class WinRTMetadataFixtureSweepCase(
    val name: String,
    val context: WinRTMetadataProjectionContext,
    val expectResolvable: Boolean = true,
    val required: Boolean = expectResolvable,
    val validateProjectionInputs: Boolean = true,
    val expectedNamespaces: Set<String> = emptySet(),
    val expectedPackageAssetKinds: Set<WinRTPackageAssetKind> = emptySet(),
)

data class WinRTMetadataFixtureSweepResult(
    val name: String,
    val resolvedFiles: List<String>,
    val packageAssets: List<WinRTPackageAsset> = emptyList(),
    val diagnosticReport: WinRTMetadataDiagnosticReport,
) {
    val passed: Boolean
        get() = !diagnosticReport.hasErrors
}

data class WinRTMetadataFixtureSweepReport(
    val results: List<WinRTMetadataFixtureSweepResult>,
) {
    val failed: List<WinRTMetadataFixtureSweepResult>
        get() = results.filterNot(WinRTMetadataFixtureSweepResult::passed)

    val passed: List<WinRTMetadataFixtureSweepResult>
        get() = results.filter(WinRTMetadataFixtureSweepResult::passed)

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
            throw WinRTMetadataDiagnosticException(WinRTMetadataDiagnosticReport(diagnostics))
        }
    }
}

object WinRTMetadataFixtureSweep {
    fun run(
        cases: List<WinRTMetadataFixtureSweepCase>,
        options: WinRTMetadataValidationOptions = WinRTMetadataValidationOptions(),
    ): WinRTMetadataFixtureSweepReport =
        WinRTMetadataFixtureSweepReport(
            cases.map { sweepCase -> runCase(sweepCase, options) },
        )

    private fun runCase(
        sweepCase: WinRTMetadataFixtureSweepCase,
        options: WinRTMetadataValidationOptions,
    ): WinRTMetadataFixtureSweepResult =
        try {
            val cache = sweepCase.context.resolveCache()
            val model = cache.load()
            val report = WinRTMetadataDiagnosticReport(
                projectionDiagnostics(sweepCase, model, options) +
                    missingNamespaceDiagnostics(sweepCase, model) +
                    missingPackageAssetDiagnostics(sweepCase, cache),
            )
            if (sweepCase.expectResolvable) {
                WinRTMetadataFixtureSweepResult(
                    name = sweepCase.name,
                    resolvedFiles = cache.files.map { it.toString() },
                    packageAssets = cache.packageAssets,
                    diagnosticReport = report,
                )
            } else {
                WinRTMetadataFixtureSweepResult(
                    name = sweepCase.name,
                    resolvedFiles = cache.files.map { it.toString() },
                    packageAssets = cache.packageAssets,
                    diagnosticReport = WinRTMetadataDiagnosticReport(
                        report.diagnostics + WinRTMetadataDiagnostic(
                            code = WinRTMetadataDiagnosticCode.InvalidCommandSpecification,
                            severity = WinRTMetadataDiagnosticSeverity.Error,
                            message = "Sweep case '${sweepCase.name}' was expected to be unresolved but resolved successfully.",
                        ),
                    ),
                )
            }
        } catch (error: IllegalArgumentException) {
            val diagnostic = WinRTMetadataDiagnostic(
                code = diagnosticCodeForSweepFailure(error.message.orEmpty()),
                severity = if (sweepCase.required) WinRTMetadataDiagnosticSeverity.Error else WinRTMetadataDiagnosticSeverity.Warning,
                message = error.message ?: "Metadata fixture sweep case '${sweepCase.name}' failed.",
            )
            WinRTMetadataFixtureSweepResult(
                name = sweepCase.name,
                resolvedFiles = emptyList(),
                diagnosticReport = WinRTMetadataDiagnosticReport(listOf(diagnostic)),
            )
        }
}

private fun diagnosticCodeForSweepFailure(message: String): WinRTMetadataDiagnosticCode =
    when {
        "missing WinMD" in message || "references missing" in message -> WinRTMetadataDiagnosticCode.MissingReferencedMetadata
        "Response file" in message || "ApiContract" in message -> WinRTMetadataDiagnosticCode.InvalidCommandSpecification
        else -> WinRTMetadataDiagnosticCode.InvalidMetadataSource
    }

private fun projectionDiagnostics(
    sweepCase: WinRTMetadataFixtureSweepCase,
    model: WinRTMetadataModel,
    options: WinRTMetadataValidationOptions,
): List<WinRTMetadataDiagnostic> =
    if (sweepCase.validateProjectionInputs) {
        model.validateForProjection(options).diagnostics
    } else {
        emptyList()
    }

private fun missingNamespaceDiagnostics(
    sweepCase: WinRTMetadataFixtureSweepCase,
    model: WinRTMetadataModel,
): List<WinRTMetadataDiagnostic> {
    val namespaces = model.namespaces.map(WinRTNamespace::name).toSet()
    return sweepCase.expectedNamespaces
        .filterNot { it in namespaces }
        .map { namespace ->
            WinRTMetadataDiagnostic(
                code = WinRTMetadataDiagnosticCode.MissingReferencedMetadata,
                severity = WinRTMetadataDiagnosticSeverity.Error,
                message = "Sweep case '${sweepCase.name}' did not load expected namespace '$namespace'.",
            )
        }
}

private fun missingPackageAssetDiagnostics(
    sweepCase: WinRTMetadataFixtureSweepCase,
    cache: WinRTMetadataCache,
): List<WinRTMetadataDiagnostic> {
    val kinds = cache.packageAssets.map(WinRTPackageAsset::kind).toSet()
    return sweepCase.expectedPackageAssetKinds
        .filterNot { it in kinds }
        .map { kind ->
            WinRTMetadataDiagnostic(
                code = WinRTMetadataDiagnosticCode.MissingReferencedMetadata,
                severity = WinRTMetadataDiagnosticSeverity.Error,
                message = "Sweep case '${sweepCase.name}' did not discover expected package asset kind '$kind'.",
            )
        }
}
