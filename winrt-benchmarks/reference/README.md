# Pinned benchmark workloads

The six C# files are unmodified copies of `src/Benchmarks` from Microsoft CsWinRT
commit `710f5293270b4633b67395d823a0071a06536c32`, under the accompanying MIT license.
They define the existing 97-operation comparison catalog and run against CsWinRT 2.2.0.

Keep these workloads stable when updating the local `.cswinrt` implementation reference.
CsWinRT 3.0 preview comments out the old `GuidPerf` class and changes runtime APIs;
referencing its checkout directly would invalidate existing benchmark comparisons.
Runtime design follows the local CsWinRT 3.0 source, independently of this historical
benchmark workload and C# runner version. A future 3.0 runner must be labeled separately.
