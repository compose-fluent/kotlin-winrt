using System.Diagnostics;
using System.Text.Json;

namespace KotlinWinRT.Benchmarks.CsWinRT;

internal static class Program
{
    private const int SchemaVersion = 3;
    private const double TargetBatchNanoseconds = 5_000_000;
    private const int MaxCalibratedIterations = 100_000;
    private const int MaxCalibrationSteps = 8;
    private const long MinAdaptiveWarmupOperations = 100_000;
    private const double MinAdaptiveWarmupNanoseconds = 500_000_000;
    private const double MaxAdaptiveWarmupNanoseconds = 1_000_000_000;
    private const int WarmupSettleRounds = 5;

    [MTAThread]
    private static int Main(string[] args)
    {
        try
        {
            Run(ParseOptions(args));
            return 0;
        }
        catch (Exception exception)
        {
            Console.Error.WriteLine(exception);
            return 1;
        }
    }

    private static void Run(BenchmarkOptions options)
    {
        IReadOnlyList<BenchmarkScenarioDefinition> definitions = ReferenceScenarios.Definitions;
        string[] duplicateNames = definitions
            .GroupBy(definition => definition.Name, StringComparer.Ordinal)
            .Where(group => group.Count() != 1)
            .Select(group => group.Key)
            .Order()
            .ToArray();
        if (duplicateNames.Length != 0)
        {
            throw new InvalidOperationException($"Duplicate benchmark scenarios: {string.Join(", ", duplicateNames)}.");
        }
        if (definitions.Count != ReferenceScenarios.ExpectedScenarioCount)
        {
            throw new InvalidOperationException(
                $"Expected {ReferenceScenarios.ExpectedScenarioCount} reference scenarios, got {definitions.Count}.");
        }

        if (options.ListScenarios)
        {
            if (options.Filter.Count != 0)
            {
                throw new InvalidOperationException("--list-scenarios cannot be combined with --filter.");
            }
            WriteOutput(
                string.Join(Environment.NewLine, definitions.Select(definition => definition.Name).Order()) +
                    Environment.NewLine,
                options.OutputPath);
            return;
        }

        var knownNames = definitions.Select(definition => definition.Name).ToHashSet(StringComparer.Ordinal);
        string[] unknownNames = options.Filter.Where(name => !knownNames.Contains(name)).Order().ToArray();
        if (unknownNames.Length != 0)
        {
            throw new InvalidOperationException($"Unknown benchmark scenarios: {string.Join(", ", unknownNames)}.");
        }

        BenchmarkScenarioDefinition[] selected = definitions
            .Where(definition => options.Filter.Count == 0 || options.Filter.Contains(definition.Name))
            .ToArray();
        if (selected.Length == 0)
        {
            throw new InvalidOperationException("No benchmark scenarios selected.");
        }

        string runtime = $"CsWinRT 2.2.0; .NET {Environment.Version}";
        var results = new List<BenchmarkResult>(selected.Length);
        foreach (BenchmarkScenarioDefinition definition in selected)
        {
            using PreparedBenchmarkScenario scenario = definition.Prepare();
            results.Add(RunScenario(scenario, options, runtime));
        }

        var serializerOptions = new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase };
        string jsonLines = string.Join(
            Environment.NewLine,
            results.Select(result => JsonSerializer.Serialize(result, serializerOptions))) + Environment.NewLine;

        WriteOutput(jsonLines, options.OutputPath);
    }

    private static void WriteOutput(string contents, string? outputPath)
    {
        if (outputPath is not null)
        {
            string? parent = Path.GetDirectoryName(Path.GetFullPath(outputPath));
            if (!string.IsNullOrEmpty(parent))
            {
                Directory.CreateDirectory(parent);
            }
            File.WriteAllText(outputPath, contents);
        }

        Console.Write(contents);
    }

    private static BenchmarkResult RunScenario(
        PreparedBenchmarkScenario scenario,
        BenchmarkOptions options,
        string runtime)
    {
        long validationChecksum = scenario.RunBatch(1);
        long expectedSingleChecksum = scenario.ExpectedSingleChecksum ?? validationChecksum;
        if (validationChecksum != expectedSingleChecksum)
        {
            throw new InvalidOperationException(
                $"Scenario '{scenario.Name}' failed correctness validation: expected " +
                $"{expectedSingleChecksum}, got {validationChecksum}.");
        }

        int iterations = CalibrateIterations(scenario, expectedSingleChecksum, options.Iterations);
        WarmupResult warmup = WarmUp(scenario, expectedSingleChecksum, iterations, options.WarmupRounds);
        iterations = CalibrateIterations(scenario, expectedSingleChecksum, iterations);
        long expectedChecksum = checked(expectedSingleChecksum * iterations);

        var samples = new double[options.MeasurementRounds];
        for (int round = 0; round < options.MeasurementRounds; round++)
        {
            long start = Stopwatch.GetTimestamp();
            long checksum = scenario.RunBatch(iterations);
            TimeSpan elapsed = Stopwatch.GetElapsedTime(start);
            if (checksum != expectedChecksum)
            {
                throw new InvalidOperationException(
                    $"Scenario '{scenario.Name}' produced checksum {checksum}; expected {expectedChecksum}.");
            }
            samples[round] = elapsed.TotalNanoseconds / iterations;
        }

        double[] sortedSamples = samples.Order().ToArray();
        return new BenchmarkResult(
            SchemaVersion,
            "cswinrt",
            runtime,
            scenario.Name,
            options.WarmupRounds,
            warmup.Rounds,
            warmup.Operations,
            options.MeasurementRounds,
            options.Iterations,
            iterations,
            sortedSamples[0],
            Median(sortedSamples),
            NearestRankPercentile(sortedSamples, 0.95),
            expectedSingleChecksum,
            samples);
    }

    private static WarmupResult WarmUp(
        PreparedBenchmarkScenario scenario,
        long expectedSingleChecksum,
        int iterations,
        int minimumRounds)
    {
        var warmup = new AdaptiveWarmup(minimumRounds);
        long expectedChecksum = checked(expectedSingleChecksum * iterations);
        while (warmup.ShouldContinue)
        {
            long start = Stopwatch.GetTimestamp();
            long checksum = scenario.RunBatch(iterations);
            double elapsedNanoseconds = Math.Max(Stopwatch.GetElapsedTime(start).TotalNanoseconds, 1.0);
            if (checksum != expectedChecksum)
            {
                throw new InvalidOperationException(
                    $"Scenario '{scenario.Name}' produced an unstable warmup checksum: " +
                    $"expected {expectedChecksum}, got {checksum}.");
            }
            warmup.RecordBatch(iterations, elapsedNanoseconds);
        }
        return new WarmupResult(warmup.Rounds, warmup.Operations);
    }

    private static int CalibrateIterations(
        PreparedBenchmarkScenario scenario,
        long expectedSingleChecksum,
        int minimumIterations)
    {
        int iterations = Math.Min(minimumIterations, MaxCalibratedIterations);
        for (int step = 0; step < MaxCalibrationSteps; step++)
        {
            long start = Stopwatch.GetTimestamp();
            long checksum = scenario.RunBatch(iterations);
            double elapsedNanoseconds = Math.Max(Stopwatch.GetElapsedTime(start).TotalNanoseconds, 1.0);
            long expectedChecksum = checked(expectedSingleChecksum * iterations);
            if (checksum != expectedChecksum)
            {
                throw new InvalidOperationException(
                    $"Scenario '{scenario.Name}' produced checksum {checksum} during calibration; " +
                    $"expected {expectedChecksum}.");
            }
            if (elapsedNanoseconds >= TargetBatchNanoseconds || iterations == MaxCalibratedIterations)
            {
                return iterations;
            }

            long scale = Math.Clamp(
                checked((long)Math.Ceiling(TargetBatchNanoseconds / elapsedNanoseconds)),
                2,
                1_000);
            iterations = checked((int)Math.Min((long)iterations * scale, MaxCalibratedIterations));
        }
        return iterations;
    }

    private static double Median(double[] sortedValues)
    {
        int middle = sortedValues.Length / 2;
        return sortedValues.Length % 2 == 0
            ? (sortedValues[middle - 1] + sortedValues[middle]) / 2.0
            : sortedValues[middle];
    }

    private static double NearestRankPercentile(double[] sortedValues, double percentile)
    {
        int index = Math.Clamp((int)Math.Ceiling(percentile * sortedValues.Length) - 1, 0, sortedValues.Length - 1);
        return sortedValues[index];
    }

    private static BenchmarkOptions ParseOptions(string[] args)
    {
        int warmupRounds = 5;
        int measurementRounds = 15;
        int iterations = 1;
        string? outputPath = null;
        var filter = new HashSet<string>(StringComparer.Ordinal);
        bool listScenarios = false;

        string NextValue(ref int index, string option)
        {
            if (index + 1 >= args.Length)
            {
                throw new InvalidOperationException($"Missing value for {option}.");
            }
            return args[++index];
        }

        for (int index = 0; index < args.Length; index++)
        {
            string option = args[index];
            switch (option)
            {
                case "--warmup-rounds":
                    warmupRounds = ParseNonNegativeInt(NextValue(ref index, option), option);
                    break;
                case "--measurement-rounds":
                    measurementRounds = ParsePositiveInt(NextValue(ref index, option), option);
                    break;
                case "--iterations":
                    iterations = ParsePositiveInt(NextValue(ref index, option), option);
                    break;
                case "--output":
                    outputPath = NextValue(ref index, option);
                    if (string.IsNullOrWhiteSpace(outputPath))
                    {
                        throw new InvalidOperationException($"{option} must not be blank.");
                    }
                    break;
                case "--filter":
                    filter = NextValue(ref index, option)
                        .Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries)
                        .ToHashSet(StringComparer.Ordinal);
                    if (filter.Count == 0)
                    {
                        throw new InvalidOperationException($"{option} must select at least one scenario.");
                    }
                    break;
                case "--list-scenarios":
                    listScenarios = true;
                    break;
                default:
                    throw new InvalidOperationException($"Unknown benchmark option '{option}'.");
            }
        }

        return new BenchmarkOptions(warmupRounds, measurementRounds, iterations, outputPath, filter, listScenarios);
    }

    private static int ParseNonNegativeInt(string value, string option) =>
        int.TryParse(value, out int parsed) && parsed >= 0
            ? parsed
            : throw new InvalidOperationException($"{option} expects a non-negative integer, got '{value}'.");

    private static int ParsePositiveInt(string value, string option) =>
        int.TryParse(value, out int parsed) && parsed > 0
            ? parsed
            : throw new InvalidOperationException($"{option} expects a positive integer, got '{value}'.");

    private sealed record BenchmarkOptions(
        int WarmupRounds,
        int MeasurementRounds,
        int Iterations,
        string? OutputPath,
        HashSet<string> Filter,
        bool ListScenarios);

    private sealed record BenchmarkResult(
        int SchemaVersion,
        string Runner,
        string Runtime,
        string Scenario,
        int WarmupRounds,
        int ActualWarmupRounds,
        long WarmupOperations,
        int MeasurementRounds,
        int MinimumIterations,
        int Iterations,
        double MinNsPerOp,
        double MedianNsPerOp,
        double P95NsPerOp,
        long Checksum,
        double[] SamplesNsPerOp);

    private readonly record struct WarmupResult(int Rounds, long Operations);

    private sealed class AdaptiveWarmup
    {
        private readonly int minimumRounds;
        private int? finalRound;

        internal AdaptiveWarmup(int minimumRounds)
        {
            if (minimumRounds < 0)
            {
                throw new ArgumentOutOfRangeException(nameof(minimumRounds));
            }
            this.minimumRounds = minimumRounds;
            finalRound = minimumRounds == 0 ? 0 : null;
        }

        internal int Rounds { get; private set; }
        internal long Operations { get; private set; }
        internal double ElapsedNanoseconds { get; private set; }
        internal bool ShouldContinue => !finalRound.HasValue || Rounds < finalRound.Value;

        internal void RecordBatch(int iterations, double elapsedNanoseconds)
        {
            if (!ShouldContinue || iterations <= 0 || elapsedNanoseconds <= 0)
            {
                throw new InvalidOperationException("Invalid adaptive warmup batch.");
            }

            Rounds = checked(Rounds + 1);
            Operations = checked(Operations + iterations);
            ElapsedNanoseconds += elapsedNanoseconds;

            if (!finalRound.HasValue && AdaptiveThresholdReached())
            {
                finalRound = Math.Max(minimumRounds, checked(Rounds + WarmupSettleRounds));
            }
        }

        private bool AdaptiveThresholdReached() =>
            ElapsedNanoseconds >= MaxAdaptiveWarmupNanoseconds ||
            (Operations >= MinAdaptiveWarmupOperations &&
                ElapsedNanoseconds >= MinAdaptiveWarmupNanoseconds);
    }
}

internal sealed record BenchmarkScenarioDefinition(
    string Name,
    Func<PreparedBenchmarkScenario> Prepare);

internal sealed class PreparedBenchmarkScenario : IDisposable
{
    private readonly Action? cleanup;

    internal PreparedBenchmarkScenario(
        string name,
        long? expectedSingleChecksum,
        Func<int, long> runBatch,
        Action? cleanup = null)
    {
        Name = name;
        ExpectedSingleChecksum = expectedSingleChecksum;
        RunBatch = runBatch;
        this.cleanup = cleanup;
    }

    internal string Name { get; }
    internal long? ExpectedSingleChecksum { get; }
    internal Func<int, long> RunBatch { get; }

    public void Dispose() => cleanup?.Invoke();
}
