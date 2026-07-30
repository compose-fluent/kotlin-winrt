#include <winrt/Windows.Data.Json.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <functional>
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
    constexpr int schema_version = 1;
    constexpr wchar_t payload[] = LR"({"name":"kotlin-winrt","verified":true,"count":42.5})";

    std::string narrow(std::wstring_view value)
    {
        return winrt::to_string(winrt::hstring{ value });
    }

    struct benchmark_options
    {
        int warmup_rounds{ 5 };
        int measurement_rounds{ 15 };
        int iterations{ 10'000 };
        std::filesystem::path output_path;
        std::set<std::string> filter;
    };

    struct benchmark_scenario
    {
        std::string name;
        std::uint64_t expected_single_checksum;
        std::function<std::uint64_t(int)> run_batch;
    };

    struct benchmark_result
    {
        std::string scenario;
        int warmup_rounds;
        int measurement_rounds;
        int iterations;
        double min_ns_per_op;
        double median_ns_per_op;
        double p95_ns_per_op;
        std::uint64_t checksum;
        std::vector<double> samples_ns_per_op;
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

    benchmark_result run_scenario(benchmark_scenario const& scenario, benchmark_options const& options)
    {
        std::uint64_t const validation_checksum = scenario.run_batch(1);
        if (validation_checksum != scenario.expected_single_checksum)
        {
            throw std::runtime_error("Scenario '" + scenario.name + "' failed correctness validation.");
        }

        std::uint64_t const expected_checksum =
            scenario.expected_single_checksum * static_cast<std::uint64_t>(options.iterations);
        for (int round = 0; round < options.warmup_rounds; ++round)
        {
            if (scenario.run_batch(options.iterations) != expected_checksum)
            {
                throw std::runtime_error("Scenario '" + scenario.name + "' produced an unstable warmup checksum.");
            }
        }

        std::vector<double> samples;
        samples.reserve(options.measurement_rounds);
        for (int round = 0; round < options.measurement_rounds; ++round)
        {
            auto const start = std::chrono::steady_clock::now();
            std::uint64_t const checksum = scenario.run_batch(options.iterations);
            auto const elapsed = std::chrono::steady_clock::now() - start;
            if (checksum != expected_checksum)
            {
                throw std::runtime_error("Scenario '" + scenario.name + "' produced an invalid checksum.");
            }
            double const elapsed_ns = std::chrono::duration<double, std::nano>(elapsed).count();
            samples.push_back(elapsed_ns / options.iterations);
        }

        std::vector<double> sorted_samples = samples;
        std::sort(sorted_samples.begin(), sorted_samples.end());
        return benchmark_result{
            scenario.name,
            options.warmup_rounds,
            options.measurement_rounds,
            options.iterations,
            sorted_samples.front(),
            median(sorted_samples),
            nearest_rank_percentile(sorted_samples, 0.95),
            expected_checksum,
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
            << ",\"measurementRounds\":" << result.measurement_rounds
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
    try
    {
        winrt::init_apartment(winrt::apartment_type::multi_threaded);
        benchmark_options const options = parse_options(argc, argv);
        using namespace winrt::Windows::Data::Json;

        JsonObject const json = JsonObject::Parse(payload);
        std::uint64_t const stringified_length = json.Stringify().size();
        std::vector<benchmark_scenario> const scenarios{
            {
                "activate_json_object",
                1,
                [](int iterations)
                {
                    std::uint64_t checksum{};
                    for (int index = 0; index < iterations; ++index)
                    {
                        if (JsonObject{}.ValueType() == JsonValueType::Object)
                        {
                            ++checksum;
                        }
                    }
                    return checksum;
                },
            },
            {
                "get_value_type",
                1,
                [json](int iterations)
                {
                    std::uint64_t checksum{};
                    for (int index = 0; index < iterations; ++index)
                    {
                        if (json.ValueType() == JsonValueType::Object)
                        {
                            ++checksum;
                        }
                    }
                    return checksum;
                },
            },
            {
                "get_named_boolean",
                1,
                [json](int iterations)
                {
                    std::uint64_t checksum{};
                    for (int index = 0; index < iterations; ++index)
                    {
                        if (json.GetNamedBoolean(L"verified"))
                        {
                            ++checksum;
                        }
                    }
                    return checksum;
                },
            },
            {
                "get_named_string",
                12,
                [json](int iterations)
                {
                    std::uint64_t checksum{};
                    for (int index = 0; index < iterations; ++index)
                    {
                        checksum += json.GetNamedString(L"name").size();
                    }
                    return checksum;
                },
            },
            {
                "stringify",
                stringified_length,
                [json](int iterations)
                {
                    std::uint64_t checksum{};
                    for (int index = 0; index < iterations; ++index)
                    {
                        checksum += json.Stringify().size();
                    }
                    return checksum;
                },
            },
            {
                "parse_get_named_number",
                42,
                [](int iterations)
                {
                    std::uint64_t checksum{};
                    for (int index = 0; index < iterations; ++index)
                    {
                        checksum += static_cast<std::uint64_t>(
                            JsonObject::Parse(payload).GetNamedNumber(L"count"));
                    }
                    return checksum;
                },
            },
        };

        std::set<std::string> known_names;
        for (auto const& scenario : scenarios)
        {
            known_names.insert(scenario.name);
        }
        for (auto const& filter : options.filter)
        {
            if (!known_names.contains(filter))
            {
                throw std::runtime_error("Unknown benchmark scenario '" + filter + "'.");
            }
        }

        std::vector<std::string> json_lines;
        for (auto const& scenario : scenarios)
        {
            if (!options.filter.empty() && !options.filter.contains(scenario.name))
            {
                continue;
            }
            json_lines.push_back(to_json(run_scenario(scenario, options)));
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
    catch (std::exception const& exception)
    {
        std::cerr << exception.what() << '\n';
        return 1;
    }
}
