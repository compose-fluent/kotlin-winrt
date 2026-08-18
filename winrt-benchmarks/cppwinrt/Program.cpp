#include "ReferenceScenarios.h"

#include <winrt/base.h>

#include <Windows.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <set>
#include <sstream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <vector>

namespace
{
    constexpr int schema_version = 3;
    constexpr std::size_t expected_scenario_count = 97;
    constexpr double target_batch_nanoseconds = 5'000'000.0;
    constexpr int max_calibrated_iterations = 100'000;
    constexpr int max_calibration_steps = 8;
    constexpr std::uint64_t min_adaptive_warmup_operations = 100'000;
    constexpr double min_adaptive_warmup_nanoseconds = 500'000'000.0;
    constexpr double max_adaptive_warmup_nanoseconds = 1'000'000'000.0;
    constexpr int warmup_settle_rounds = 5;

    std::string narrow(std::wstring_view value)
    {
        return winrt::to_string(winrt::hstring{ value });
    }

    struct benchmark_options
    {
        int warmup_rounds{ 5 };
        int measurement_rounds{ 15 };
        int iterations{ 1 };
        std::filesystem::path output_path;
        std::set<std::string> filter;
        bool list_scenarios{};
    };

    struct benchmark_result
    {
        std::string scenario;
        int warmup_rounds;
        int actual_warmup_rounds;
        std::uint64_t warmup_operations;
        int measurement_rounds;
        int minimum_iterations;
        int iterations;
        double min_ns_per_op;
        double median_ns_per_op;
        double p95_ns_per_op;
        std::uint64_t checksum;
        std::vector<double> samples_ns_per_op;
    };

    struct adaptive_warmup
    {
        explicit adaptive_warmup(int minimum) :
            minimum_rounds(minimum),
            final_round(minimum == 0 ? 0 : -1)
        {
            if (minimum < 0)
            {
                throw std::invalid_argument("Minimum warmup rounds must be non-negative.");
            }
        }

        bool should_continue() const noexcept
        {
            return final_round < 0 || rounds < final_round;
        }

        void record_batch(int iterations, double elapsed_ns)
        {
            if (!should_continue() || iterations <= 0 || elapsed_ns <= 0.0)
            {
                throw std::runtime_error("Invalid adaptive warmup batch.");
            }

            ++rounds;
            operations += static_cast<std::uint64_t>(iterations);
            elapsed_nanoseconds += elapsed_ns;
            if (final_round < 0 && threshold_reached())
            {
                final_round = std::max(minimum_rounds, rounds + warmup_settle_rounds);
            }
        }

        int minimum_rounds;
        int final_round;
        int rounds{};
        std::uint64_t operations{};
        double elapsed_nanoseconds{};

    private:
        bool threshold_reached() const noexcept
        {
            return elapsed_nanoseconds >= max_adaptive_warmup_nanoseconds ||
                (operations >= min_adaptive_warmup_operations &&
                    elapsed_nanoseconds >= min_adaptive_warmup_nanoseconds);
        }
    };

    int parse_non_negative_int(std::wstring const& value, std::wstring const& option)
    {
        std::size_t parsed_characters{};
        int const parsed = std::stoi(value, &parsed_characters);
        if (parsed_characters != value.size() || parsed < 0)
        {
            throw std::runtime_error(narrow(option + L" expects a non-negative integer."));
        }
        return parsed;
    }

    int parse_positive_int(std::wstring const& value, std::wstring const& option)
    {
        int const parsed = parse_non_negative_int(value, option);
        if (parsed == 0)
        {
            throw std::runtime_error(narrow(option + L" expects a positive integer."));
        }
        return parsed;
    }

    benchmark_options parse_options(int argc, wchar_t** argv)
    {
        benchmark_options options;
        auto next_value = [&](int& index, std::wstring const& option) -> std::wstring
        {
            if (index + 1 >= argc)
            {
                throw std::runtime_error(narrow(L"Missing value for " + option + L"."));
            }
            return argv[++index];
        };

        for (int index = 1; index < argc; ++index)
        {
            std::wstring const option = argv[index];
            if (option == L"--warmup-rounds")
            {
                options.warmup_rounds = parse_non_negative_int(next_value(index, option), option);
            }
            else if (option == L"--measurement-rounds")
            {
                options.measurement_rounds = parse_positive_int(next_value(index, option), option);
            }
            else if (option == L"--iterations")
            {
                options.iterations = parse_positive_int(next_value(index, option), option);
            }
            else if (option == L"--output")
            {
                options.output_path = next_value(index, option);
                if (options.output_path.empty())
                {
                    throw std::runtime_error("--output must not be blank.");
                }
            }
            else if (option == L"--filter")
            {
                std::string const filter = narrow(next_value(index, option));
                std::size_t start{};
                while (start <= filter.size())
                {
                    std::size_t const separator = filter.find(',', start);
                    std::string const value = filter.substr(start, separator - start);
                    if (!value.empty())
                    {
                        options.filter.insert(value);
                    }
                    if (separator == std::string::npos)
                    {
                        break;
                    }
                    start = separator + 1;
                }
                if (options.filter.empty())
                {
                    throw std::runtime_error("--filter must select at least one scenario.");
                }
            }
            else if (option == L"--list-scenarios")
            {
                options.list_scenarios = true;
            }
            else
            {
                throw std::runtime_error("Unknown benchmark option '" + narrow(option) + "'.");
            }
        }
        return options;
    }

    double median(std::vector<double> const& sorted_values)
    {
        std::size_t const middle = sorted_values.size() / 2;
        return sorted_values.size() % 2 == 0
            ? (sorted_values[middle - 1] + sorted_values[middle]) / 2.0
            : sorted_values[middle];
    }

    double nearest_rank_percentile(std::vector<double> const& sorted_values, double percentile)
    {
        std::size_t const rank = static_cast<std::size_t>(std::ceil(percentile * sorted_values.size()));
        std::size_t const index = std::clamp<std::size_t>(rank - 1, 0, sorted_values.size() - 1);
        return sorted_values[index];
    }

    int calibrate_iterations(
        benchmark::benchmark_scenario const& scenario,
        benchmark::prepared_benchmark_scenario const& prepared,
        std::uint64_t expected_single_checksum,
        int minimum_iterations)
    {
        int iterations = std::min(minimum_iterations, max_calibrated_iterations);
        for (int step = 0; step < max_calibration_steps; ++step)
        {
            auto const start = std::chrono::steady_clock::now();
            std::uint64_t const checksum = prepared.run_batch(iterations);
            auto const elapsed = std::chrono::steady_clock::now() - start;
            double const elapsed_ns = std::max(
                std::chrono::duration<double, std::nano>(elapsed).count(),
                1.0);
            std::uint64_t const expected_checksum =
                expected_single_checksum * static_cast<std::uint64_t>(iterations);
            if (checksum != expected_checksum)
            {
                throw std::runtime_error(
                    "Scenario '" + scenario.name + "' produced an invalid checksum during calibration.");
            }
            if (elapsed_ns >= target_batch_nanoseconds || iterations == max_calibrated_iterations)
            {
                return iterations;
            }

            std::uint64_t const scale = std::clamp<std::uint64_t>(
                static_cast<std::uint64_t>(std::ceil(target_batch_nanoseconds / elapsed_ns)),
                2,
                1'000);
            iterations = static_cast<int>(std::min<std::uint64_t>(
                static_cast<std::uint64_t>(iterations) * scale,
                max_calibrated_iterations));
        }
        return iterations;
    }

    adaptive_warmup warm_up(
        benchmark::benchmark_scenario const& scenario,
        benchmark::prepared_benchmark_scenario const& prepared,
        std::uint64_t expected_single_checksum,
        int iterations,
        int minimum_rounds)
    {
        adaptive_warmup warmup{ minimum_rounds };
        std::uint64_t const expected_checksum =
            expected_single_checksum * static_cast<std::uint64_t>(iterations);
        while (warmup.should_continue())
        {
            auto const start = std::chrono::steady_clock::now();
            std::uint64_t const checksum = prepared.run_batch(iterations);
            auto const elapsed = std::chrono::steady_clock::now() - start;
            double const elapsed_ns = std::max(
                std::chrono::duration<double, std::nano>(elapsed).count(),
                1.0);
            if (checksum != expected_checksum)
            {
                throw std::runtime_error(
                    "Scenario '" + scenario.name + "' produced an unstable warmup checksum.");
            }
            warmup.record_batch(iterations, elapsed_ns);
        }
        return warmup;
    }

    benchmark_result run_scenario(
        benchmark::benchmark_scenario const& scenario,
        benchmark::prepared_benchmark_scenario const& prepared,
        benchmark_options const& options)
    {
        std::uint64_t const validation_checksum = prepared.run_batch(1);
        if (scenario.expected_single_checksum && validation_checksum != *scenario.expected_single_checksum)
        {
            throw std::runtime_error("Scenario '" + scenario.name + "' failed correctness validation.");
        }

        std::uint64_t const expected_single_checksum =
            scenario.expected_single_checksum.value_or(validation_checksum);
        int iterations = calibrate_iterations(
            scenario,
            prepared,
            expected_single_checksum,
            options.iterations);
        adaptive_warmup const warmup = warm_up(
            scenario,
            prepared,
            expected_single_checksum,
            iterations,
            options.warmup_rounds);
        iterations = calibrate_iterations(scenario, prepared, expected_single_checksum, iterations);
        std::uint64_t const expected_checksum =
            expected_single_checksum * static_cast<std::uint64_t>(iterations);

        std::vector<double> samples;
        samples.reserve(options.measurement_rounds);
        for (int round = 0; round < options.measurement_rounds; ++round)
        {
            auto const start = std::chrono::steady_clock::now();
            std::uint64_t const checksum = prepared.run_batch(iterations);
            auto const elapsed = std::chrono::steady_clock::now() - start;
            if (checksum != expected_checksum)
            {
                throw std::runtime_error("Scenario '" + scenario.name + "' produced an invalid checksum.");
            }
            double const elapsed_ns = std::chrono::duration<double, std::nano>(elapsed).count();
            samples.push_back(elapsed_ns / iterations);
        }

        std::vector<double> sorted_samples = samples;
        std::sort(sorted_samples.begin(), sorted_samples.end());
        return benchmark_result{
            scenario.name,
            options.warmup_rounds,
            warmup.rounds,
            warmup.operations,
            options.measurement_rounds,
            options.iterations,
            iterations,
            sorted_samples.front(),
            median(sorted_samples),
            nearest_rank_percentile(sorted_samples, 0.95),
            expected_single_checksum,
            std::move(samples),
        };
    }

    std::string to_json(benchmark_result const& result)
    {
        std::ostringstream output;
        output << std::setprecision(17)
            << "{\"schemaVersion\":" << schema_version
            << ",\"runner\":\"cppwinrt\""
            << ",\"runtime\":\"C++/WinRT " CPPWINRT_VERSION "\""
            << ",\"scenario\":\"" << result.scenario << "\""
            << ",\"warmupRounds\":" << result.warmup_rounds
            << ",\"actualWarmupRounds\":" << result.actual_warmup_rounds
            << ",\"warmupOperations\":" << result.warmup_operations
            << ",\"measurementRounds\":" << result.measurement_rounds
            << ",\"minimumIterations\":" << result.minimum_iterations
            << ",\"iterations\":" << result.iterations
            << ",\"minNsPerOp\":" << result.min_ns_per_op
            << ",\"medianNsPerOp\":" << result.median_ns_per_op
            << ",\"p95NsPerOp\":" << result.p95_ns_per_op
            << ",\"checksum\":" << result.checksum
            << ",\"samplesNsPerOp\":[";
        for (std::size_t index = 0; index < result.samples_ns_per_op.size(); ++index)
        {
            if (index != 0)
            {
                output << ',';
            }
            output << result.samples_ns_per_op[index];
        }
        output << "]}";
        return output.str();
    }
}

int wmain(int argc, wchar_t** argv)
{
    std::string current_scenario;
    std::string current_phase;
    try
    {
        winrt::init_apartment(winrt::apartment_type::multi_threaded);
        benchmark_options const options = parse_options(argc, argv);
        bool const trace = GetEnvironmentVariableW(L"KOTLIN_WINRT_BENCHMARK_TRACE", nullptr, 0) != 0;
        std::vector<benchmark::benchmark_scenario> const scenarios = benchmark::reference_scenarios();
        if (scenarios.size() != expected_scenario_count)
        {
            throw std::runtime_error(
                "Expected " + std::to_string(expected_scenario_count) +
                " reference scenarios, got " + std::to_string(scenarios.size()) + ".");
        }

        std::set<std::string> known_names;
        for (auto const& scenario : scenarios)
        {
            if (!known_names.insert(scenario.name).second)
            {
                throw std::runtime_error("Duplicate benchmark scenario '" + scenario.name + "'.");
            }
        }
        for (auto const& filter : options.filter)
        {
            if (!known_names.contains(filter))
            {
                throw std::runtime_error("Unknown benchmark scenario '" + filter + "'.");
            }
        }

        if (options.list_scenarios)
        {
            if (!options.filter.empty())
            {
                throw std::runtime_error("--list-scenarios cannot be combined with --filter.");
            }
            std::ostringstream catalog;
            for (auto const& name : known_names)
            {
                catalog << name << '\n';
            }
            if (!options.output_path.empty())
            {
                if (auto const parent = options.output_path.parent_path(); !parent.empty())
                {
                    std::filesystem::create_directories(parent);
                }
                std::ofstream output(options.output_path, std::ios::binary | std::ios::trunc);
                if (!output)
                {
                    throw std::runtime_error("Unable to open benchmark catalog output file.");
                }
                output << catalog.str();
            }
            std::cout << catalog.str();
            return 0;
        }

        std::vector<std::string> json_lines;
        for (auto const& scenario : scenarios)
        {
            if (!options.filter.empty() && !options.filter.contains(scenario.name))
            {
                continue;
            }
            current_scenario = scenario.name;
            current_phase = "prepare";
            if (trace) std::cerr << current_scenario << " prepare\n";
            auto prepared = scenario.prepare();
            try
            {
                current_phase = "run";
                if (trace) std::cerr << current_scenario << " run\n";
                json_lines.push_back(to_json(run_scenario(scenario, prepared, options)));
            }
            catch (...)
            {
                current_phase = "cleanup after failure";
                prepared.cleanup();
                throw;
            }
            current_phase = "cleanup";
            if (trace) std::cerr << current_scenario << " cleanup\n";
            prepared.cleanup();
            if (trace) std::cerr << current_scenario << " done\n";
        }
        if (json_lines.empty())
        {
            throw std::runtime_error("No benchmark scenarios selected.");
        }

        if (!options.output_path.empty())
        {
            if (auto const parent = options.output_path.parent_path(); !parent.empty())
            {
                std::filesystem::create_directories(parent);
            }
            std::ofstream output(options.output_path, std::ios::binary | std::ios::trunc);
            if (!output)
            {
                throw std::runtime_error("Unable to open benchmark output file.");
            }
            for (auto const& line : json_lines)
            {
                output << line << '\n';
            }
        }

        for (auto const& line : json_lines)
        {
            std::cout << line << '\n';
        }
        return 0;
    }
    catch (winrt::hresult_error const& exception)
    {
        std::cerr << current_scenario << " [" << current_phase << "]: "
            << winrt::to_string(exception.message()) << " (HRESULT 0x"
            << std::hex << static_cast<std::uint32_t>(exception.code()) << ")\n";
        return 1;
    }
    catch (std::exception const& exception)
    {
        std::cerr << exception.what() << '\n';
        return 1;
    }
}
