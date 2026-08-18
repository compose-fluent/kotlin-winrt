#pragma once

#include <cstdint>
#include <functional>
#include <optional>
#include <string>
#include <vector>

namespace benchmark
{
    struct prepared_benchmark_scenario
    {
        std::function<std::uint64_t(int)> run_batch;
        std::function<void()> cleanup;
    };

    struct benchmark_scenario
    {
        std::string name;
        std::optional<std::uint64_t> expected_single_checksum;
        std::function<prepared_benchmark_scenario()> prepare;
    };

    std::vector<benchmark_scenario> reference_scenarios();
}
