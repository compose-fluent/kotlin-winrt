using System.Diagnostics;
using System.Text.Json;
using Windows.Data.Json;

namespace KotlinWinRT.Benchmarks.CsWinRT;

internal static class Program
{
    private const int SchemaVersion = 1;
    private const string Payload = "{\"name\":\"kotlin-winrt\",\"verified\":true,\"count\":42.5}";

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
        JsonObject json = JsonObject.Parse(Payload);
        JsonArray jsonArray = JsonArray.Parse("[42.5]");
        long stringifiedLength = json.Stringify().Length;
        var scenarios = new[]
        {
            new BenchmarkScenario(
                "activate_json_object",
                1,
                iterations =>
                {
                    long checksum = 0;
                    for (int index = 0; index < iterations; index++)
                    {
                        var value = new JsonObject();
                        if (value.ValueType == JsonValueType.Object)
                        {
                            checksum++;
                        }
                    }
                    return checksum;
                }),
            new BenchmarkScenario(
                "get_value_type",
                1,
                iterations =>
                {
                    long checksum = 0;
                    for (int index = 0; index < iterations; index++)
                    {
                        if (json.ValueType == JsonValueType.Object)
                        {
                            checksum++;
                        }
                    }
                    return checksum;
                }),
            new BenchmarkScenario(
                "get_array_number_at",
                42,
                iterations =>
                {
                    long checksum = 0;
                    for (int index = 0; index < iterations; index++)
                    {
                        checksum += (long)jsonArray.GetNumberAt(0);
                    }
                    return checksum;
                }),
            new BenchmarkScenario(
                "get_named_boolean",
                1,
                iterations =>
                {
                    long checksum = 0;
                    for (int index = 0; index < iterations; index++)
                    {
                        if (json.GetNamedBoolean("verified"))
                        {
                            checksum++;
                        }
                    }
                    return checksum;
                }),
            new BenchmarkScenario(
                "get_named_string",
                "kotlin-winrt".Length,
                iterations =>
                {
                    long checksum = 0;
                    for (int index = 0; index < iterations; index++)
                    {
                        checksum += json.GetNamedString("name").Length;
                    }
                    return checksum;
                }),
            new BenchmarkScenario(
                "stringify",
                stringifiedLength,
                iterations =>
                {
                    long checksum = 0;
                    for (int index = 0; index < iterations; index++)
                    {
                        checksum += json.Stringify().Length;
                    }
                    return checksum;
                }),
            new BenchmarkScenario(
                "parse_get_named_number",
                42,
                iterations =>
                {
                    long checksum = 0;
                    for (int index = 0; index < iterations; index++)
                    {
                        checksum += (long)JsonObject.Parse(Payload).GetNamedNumber("count");
                    }
                    return checksum;
                }),
        };

        var knownNames = scenarios.Select(scenario => scenario.Name).ToHashSet(StringComparer.Ordinal);
        string[] unknownNames = options.Filter.Where(name => !knownNames.Contains(name)).Order().ToArray();
        if (unknownNames.Length != 0)
        {
            throw new InvalidOperationException($"Unknown benchmark scenarios: {string.Join(", ", unknownNames)}.");
        }

        BenchmarkScenario[] selected = scenarios
            .Where(scenario => options.Filter.Count == 0 || options.Filter.Contains(scenario.Name))
            .ToArray();
        if (selected.Length == 0)
        {
            throw new InvalidOperationException("No benchmark scenarios selected.");
        }

        string runtime = $"CsWinRT 2.2.0; .NET {Environment.Version}";
        BenchmarkResult[] results = selected
            .Select(scenario => RunScenario(scenario, options, runtime))
            .ToArray();
        var serializerOptions = new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase };
        string jsonLines = string.Join(
            Environment.NewLine,
            results.Select(result => JsonSerializer.Serialize(result, serializerOptions))) + Environment.NewLine;

        if (options.OutputPath is not null)
        {
            string? parent = Path.GetDirectoryName(Path.GetFullPath(options.OutputPath));
            if (!string.IsNullOrEmpty(parent))
            {
                Directory.CreateDirectory(parent);
            }
            File.WriteAllText(options.OutputPath, jsonLines);
        }

        Console.Write(jsonLines);
        GC.KeepAlive(json);
        GC.KeepAlive(jsonArray);
    }

    private static BenchmarkResult RunScenario(
        BenchmarkScenario scenario,
        BenchmarkOptions options,
        string runtime)
    {
        long validationChecksum = scenario.RunBatch(1);
        if (validationChecksum != scenario.ExpectedSingleChecksum)
        {
            throw new InvalidOperationException(
                $"Scenario '{scenario.Name}' failed correctness validation: expected " +
                $"{scenario.ExpectedSingleChecksum}, got {validationChecksum}.");
        }

        long expectedChecksum = scenario.ExpectedSingleChecksum * options.Iterations;
        for (int round = 0; round < options.WarmupRounds; round++)
        {
            long checksum = scenario.RunBatch(options.Iterations);
            if (checksum != expectedChecksum)
            {
                throw new InvalidOperationException(
                    $"Scenario '{scenario.Name}' produced an unstable warmup checksum.");
            }
        }

        var samples = new double[options.MeasurementRounds];
        for (int round = 0; round < options.MeasurementRounds; round++)
        {
            long start = Stopwatch.GetTimestamp();
            long checksum = scenario.RunBatch(options.Iterations);
            TimeSpan elapsed = Stopwatch.GetElapsedTime(start);
            if (checksum != expectedChecksum)
            {
                throw new InvalidOperationException(
                    $"Scenario '{scenario.Name}' produced checksum {checksum}; expected {expectedChecksum}.");
            }
            samples[round] = elapsed.TotalNanoseconds / options.Iterations;
        }

        double[] sortedSamples = samples.Order().ToArray();
        return new BenchmarkResult(
            SchemaVersion,
            "cswinrt",
            runtime,
            scenario.Name,
            options.WarmupRounds,
            options.MeasurementRounds,
            options.Iterations,
            sortedSamples[0],
            Median(sortedSamples),
            NearestRankPercentile(sortedSamples, 0.95),
            expectedChecksum,
            samples);
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
        int iterations = 10_000;
        string? outputPath = null;
        var filter = new HashSet<string>(StringComparer.Ordinal);

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
                default:
                    throw new InvalidOperationException($"Unknown benchmark option '{option}'.");
            }
        }

        return new BenchmarkOptions(warmupRounds, measurementRounds, iterations, outputPath, filter);
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
        HashSet<string> Filter);

    private sealed record BenchmarkScenario(
        string Name,
        long ExpectedSingleChecksum,
        Func<int, long> RunBatch);

    private sealed record BenchmarkResult(
        int SchemaVersion,
        string Runner,
        string Runtime,
        string Scenario,
        int WarmupRounds,
        int MeasurementRounds,
        int Iterations,
        double MinNsPerOp,
        double MedianNsPerOp,
        double P95NsPerOp,
        long Checksum,
        double[] SamplesNsPerOp);
}
