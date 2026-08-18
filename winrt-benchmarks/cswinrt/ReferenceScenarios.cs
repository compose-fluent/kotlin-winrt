using Benchmarks;

namespace KotlinWinRT.Benchmarks.CsWinRT;

internal static class ReferenceScenarios
{
    private static object? objectResultSink;

    internal const int ExpectedScenarioCount = 97;

    internal static IReadOnlyList<BenchmarkScenarioDefinition> Definitions { get; } =
        QueryInterfaceDefinitions()
            .Concat(EventDefinitions())
            .Concat(GuidDefinitions())
            .Concat(ReflectionDefinitions())
            .Concat(AsyncDefinitions())
            .Concat(NonAgileObjectDefinitions())
            .ToArray();

    private static IEnumerable<BenchmarkScenarioDefinition> QueryInterfaceDefinitions()
    {
        yield return Value<QueryInterfacePerf, int>("QueryInterfacePerf.QueryDefaultInterface", Setup, value => value.QueryDefaultInterface(), value => value);
        yield return Value<QueryInterfacePerf, int>("QueryInterfacePerf.QueryNonDefaultInterface", Setup, value => value.QueryNonDefaultInterface(), value => value);
        yield return Value<QueryInterfacePerf, int>("QueryInterfacePerf.QueryFastAbiDefaultInterface", Setup, value => value.QueryFastAbiDefaultInterface(), value => value);
        yield return Value<QueryInterfacePerf, int>("QueryInterfacePerf.QueryFastAbiNonDefaultInterface", Setup, value => value.QueryFastAbiNonDefaultInterface(), value => value);
        yield return Value<QueryInterfacePerf, int>("QueryInterfacePerf.QueryFastAbiDerivedDefaultInterface", Setup, value => value.QueryFastAbiDerivedDefaultInterface(), value => value);
        yield return Value<QueryInterfacePerf, int>("QueryInterfacePerf.QueryFastAbiComposedNonDefaultInterface", Setup, value => value.QueryFastAbiComposedNonDefaultInterface(), value => value);
        yield return Value<QueryInterfacePerf, int>("QueryInterfacePerf.QueryFastAbiComposedBaseDefaultInterface", Setup, value => value.QueryFastAbiComposedBaseDefaultInterface(), value => value);
        yield return Value<QueryInterfacePerf, int>("QueryInterfacePerf.QueryFastAbiComposedBaseNonDefaultInterface", Setup, value => value.QueryFastAbiComposedBaseNonDefaultInterface(), value => value);
        yield return Value<QueryInterfacePerf, bool>("QueryInterfacePerf.QueryNonDefaultInterface2", Setup, value => value.QueryNonDefaultInterface2(), value => value ? 1 : 0);
        yield return Void<QueryInterfacePerf>("QueryInterfacePerf.QueryDefaultInterfaceSetProperty", Setup, value => value.QueryDefaultInterfaceSetProperty());
        yield return Void<QueryInterfacePerf>("QueryInterfacePerf.QueryNonDefaultInterfaceSetProperty", Setup, value => value.QueryNonDefaultInterfaceSetProperty());
        yield return Value<QueryInterfacePerf, bool>("QueryInterfacePerf.QuerySDKDefaultInterface", Setup, value => value.QuerySDKDefaultInterface(), value => value ? 1 : 0);
        yield return Value<QueryInterfacePerf, bool>("QueryInterfacePerf.QuerySDKNonDefaultInterface", Setup, value => value.QuerySDKNonDefaultInterface(), value => value ? 1 : 0);
        yield return Object<QueryInterfacePerf>("QueryInterfacePerf.DefaultObjectParameters", Setup, value => value.DefaultObjectParameters());
        yield return Object<QueryInterfacePerf>("QueryInterfacePerf.DefaultStringParameters", Setup, value => value.DefaultStringParameters());
        yield return Object<QueryInterfacePerf>("QueryInterfacePerf.DynamicCast", Setup, value => value.DynamicCast());
        yield return Value<QueryInterfacePerf, int>("QueryInterfacePerf.ConstructAndQueryDefaultInterfaceFirstCall", Setup, value => value.ConstructAndQueryDefaultInterfaceFirstCall(), value => value);
        yield return Value<QueryInterfacePerf, int>("QueryInterfacePerf.ConstructAndQueryNonDefaultInterfaceFirstCall", Setup, value => value.ConstructAndQueryNonDefaultInterfaceFirstCall(), value => value);
        yield return Value<QueryInterfacePerf, int>("QueryInterfacePerf.ConstructAndQueryFastAbiDefaultInterfaceFirstCall", Setup, value => value.ConstructAndQueryFastAbiDefaultInterfaceFirstCall(), value => value);
        yield return Value<QueryInterfacePerf, int>("QueryInterfacePerf.ConstructAndQueryFastAbiNonDefaultInterfaceFirstCall", Setup, value => value.ConstructAndQueryFastAbiNonDefaultInterfaceFirstCall(), value => value);
        yield return Value<QueryInterfacePerf, int>("QueryInterfacePerf.ConstructAndQueryFastAbiDerivedDefaultInterfaceFirstCall", Setup, value => value.ConstructAndQueryFastAbiDerivedDefaultInterfaceFirstCall(), value => value);
        yield return Value<QueryInterfacePerf, int>("QueryInterfacePerf.ConstructAndQueryFastAbiDerivedNonDefaultInterfaceFirstCall", Setup, value => value.ConstructAndQueryFastAbiDerivedNonDefaultInterfaceFirstCall(), value => value);
        yield return Value<QueryInterfacePerf, int>("QueryInterfacePerf.ConstructAndQueryFastAbiDerivedBaseDefaultInterfaceFirstCall", Setup, value => value.ConstructAndQueryFastAbiDerivedBaseDefaultInterfaceFirstCall(), value => value);
        yield return Value<QueryInterfacePerf, int>("QueryInterfacePerf.ConstructAndQueryFastAbiDerivedBaseNonDefaultInterfaceFirstCall", Setup, value => value.ConstructAndQueryFastAbiDerivedBaseNonDefaultInterfaceFirstCall(), value => value);
        yield return Value<QueryInterfacePerf, int>("QueryInterfacePerf.StaticPropertyCall", Setup, value => value.StaticPropertyCall(), value => value);
        yield return Void<QueryInterfacePerf>("QueryInterfacePerf.QueryInterfaceOnManagedObject", Setup, value => value.QueryInterfaceOnManagedObject());
        yield return Void<QueryInterfacePerf>("QueryInterfacePerf.QueryNativeInterfaceOnComposedObject", Setup, value => value.QueryNativeInterfaceOnComposedObject());

        static void Setup(QueryInterfacePerf value) => value.Setup();
    }

    private static IEnumerable<BenchmarkScenarioDefinition> EventDefinitions()
    {
        yield return Object<EventPerf>("EventPerf.IntEventOverhead", Setup, value => value.IntEventOverhead());
        yield return Object<EventPerf>("EventPerf.AddIntEventToNewEventSource", Setup, value => value.AddIntEventToNewEventSource());
        yield return Object<EventPerf>("EventPerf.AddMultipleEventsToNewEventSource", Setup, value => value.AddMultipleEventsToNewEventSource());
        yield return Object<EventPerf>("EventPerf.AddAndInvokeIntEventOnNewEventSource", Setup, value => value.AddAndInvokeIntEventOnNewEventSource());
        yield return Object<EventPerf>("EventPerf.AddAndInvokeMultipleIntEventsToSameEventSource", Setup, value => value.AddAndInvokeMultipleIntEventsToSameEventSource());
        yield return Value<EventPerf, int>("EventPerf.InvokeIntEvent", Setup, value => value.InvokeIntEvent(), value => value);
        yield return Value<EventPerf, int>("EventPerf.InvokeIntEventWithSenderCheck", Setup, value => value.InvokeIntEventWithSenderCheck(), value => value);
        yield return Object<EventPerf>("EventPerf.AddAndRemoveIntEventOnNewEventSource", Setup, value => value.AddAndRemoveIntEventOnNewEventSource());
        yield return Object<EventPerf>("EventPerf.NativeIntEventOverhead", Setup, value => value.NativeIntEventOverhead());
        yield return Object<EventPerf>("EventPerf.AddNativeIntEventToNewEventSource", Setup, value => value.AddNativeIntEventToNewEventSource());
        yield return Object<EventPerf>("EventPerf.AddMultipleNativeEventsToNewEventSource", Setup, value => value.AddMultipleNativeEventsToNewEventSource());
        yield return Object<EventPerf>("EventPerf.AddAndInvokeNativeIntEventOnNewEventSource", Setup, value => value.AddAndInvokeNativeIntEventOnNewEventSource());
        yield return Void<EventPerf>("EventPerf.InvokeNativeIntEvent", Setup, value => value.InvokeNativeIntEvent());
        yield return Object<EventPerf>("EventPerf.AddAndRemoveNativeIntEventOnNewEventSource", Setup, value => value.AddAndRemoveNativeIntEventOnNewEventSource());

        static void Setup(EventPerf value) => value.Setup();
    }

    private static IEnumerable<BenchmarkScenarioDefinition> GuidDefinitions()
    {
        yield return Object<GuidPerf>("GuidPerf.GetClassGuid", null, value => value.GetClassGuid());
        yield return Object<GuidPerf>("GuidPerf.GetDelegateGuid", null, value => value.GetDelegateGuid());
        yield return Object<GuidPerf>("GuidPerf.CreateListGuid", null, value => value.CreateListGuid());
        yield return Object<GuidPerf>("GuidPerf.CreateDictionaryWithStringKeyGuid", null, value => value.CreateDictionaryWithStringKeyGuid());
        yield return Object<GuidPerf>("GuidPerf.CreateDictionaryWithBoolKeyGuid", null, value => value.CreateDictionaryWithBoolKeyGuid());
        yield return Object<GuidPerf>("GuidPerf.CreateReadOnlyEnumListGuid", null, value => value.CreateReadOnlyEnumListGuid());
        yield return Object<GuidPerf>("GuidPerf.CreateReadOnlyFlagEnumListGuid", null, value => value.CreateReadOnlyFlagEnumListGuid());
        yield return Object<GuidPerf>("GuidPerf.CreateReadOnlyStructListGuid", null, value => value.CreateReadOnlyStructListGuid());
        yield return Object<GuidPerf>("GuidPerf.CreateReadOnlyInterfaceListGuid", null, value => value.CreateReadOnlyInterfaceListGuid());
        yield return Object<GuidPerf>("GuidPerf.CreateReadOnlyClassListGuid", null, value => value.CreateReadOnlyClassListGuid());
        yield return Object<GuidPerf>("GuidPerf.CreateReadOnlyDelegateListGuid", null, value => value.CreateReadOnlyDelegateListGuid());
    }

    private static IEnumerable<BenchmarkScenarioDefinition> ReflectionDefinitions()
    {
        yield return Object<ReflectionPerf>("ReflectionPerf.ExecuteMarshalingForNewKeyValuePair", Setup, value => value.ExecuteMarshalingForNewKeyValuePair());
        yield return Object<ReflectionPerf>("ReflectionPerf.ExecuteMarshalingForNewArray", Setup, value => value.ExecuteMarshalingForNewArray());
        yield return Object<ReflectionPerf>("ReflectionPerf.ExecuteMarshalingForNewNullable", Setup, value => value.ExecuteMarshalingForNewNullable());
        yield return Object<ReflectionPerf>("ReflectionPerf.ExecuteMarshalingForExistingKeyvaluePair", Setup, value => value.ExecuteMarshalingForExistingKeyvaluePair());
        yield return Object<ReflectionPerf>("ReflectionPerf.ExecuteMarshalingForExistingArray", Setup, value => value.ExecuteMarshalingForExistingArray());
        yield return Object<ReflectionPerf>("ReflectionPerf.ExecuteMarshalingForExistingNullable", Setup, value => value.ExecuteMarshalingForExistingNullable());
        yield return Object<ReflectionPerf>("ReflectionPerf.ExecuteMarshalingForString", Setup, value => value.ExecuteMarshalingForString());
        yield return Object<ReflectionPerf>("ReflectionPerf.ExecuteMarshalingForCustomObject", Setup, value => value.ExecuteMarshalingForCustomObject());
        yield return Value<ReflectionPerf, int>("ReflectionPerf.ExecuteMarshalingForDelegate", Setup, value => value.ExecuteMarshalingForDelegate(), value => value);
        yield return Void<ReflectionPerf>("ReflectionPerf.IntEventSource", Setup, value => value.IntEventSource());
        yield return Value<ReflectionPerf, int>("ReflectionPerf.ExistingDictionaryLookup", Setup, value => value.ExistingDictionaryLookup(), value => value);
        yield return Object<ReflectionPerf>("ReflectionPerf.ExistingDictionaryLookupCached", Setup, value => value.ExistingDictionaryLookupCached());
        yield return Object<ReflectionPerf>("ReflectionPerf.ExistingDictionaryLookup2", Setup, value => value.ExistingDictionaryLookup2());
        yield return Object<ReflectionPerf>("ReflectionPerf.ExistingDictionaryLookup3", Setup, value => value.ExistingDictionaryLookup3());
        yield return Value<ReflectionPerf, int>("ReflectionPerf.GetNullableInt", Setup, value => value.GetNullableInt(), value => value);
        yield return Void<ReflectionPerf>("ReflectionPerf.SetNullableInt", Setup, value => value.SetNullableInt());
        yield return Object<ReflectionPerf>("ReflectionPerf.GetNullableBittableStruct", Setup, value => value.GetNullableBittableStruct());
        yield return Void<ReflectionPerf>("ReflectionPerf.SetNullableBittableStruct", Setup, value => value.SetNullableBittableStruct());
        yield return Object<ReflectionPerf>("ReflectionPerf.GetNullableTimeSpan", Setup, value => value.GetNullableTimeSpan());
        yield return Void<ReflectionPerf>("ReflectionPerf.SetNullableTimeSpan", Setup, value => value.SetNullableTimeSpan());
        yield return Object<ReflectionPerf>("ReflectionPerf.GetNullableNonBittableStruct", Setup, value => value.GetNullableNonBittableStruct());
        yield return Void<ReflectionPerf>("ReflectionPerf.SetNullableNonBittableStruct", Setup, value => value.SetNullableNonBittableStruct());
        yield return Void<ReflectionPerf>("ReflectionPerf.SetNullableDelegate", Setup, value => value.SetNullableDelegate());
        yield return Void<ReflectionPerf>("ReflectionPerf.SetNullableIntDelegate", Setup, value => value.SetNullableIntDelegate());
        yield return Object<ReflectionPerf>("ReflectionPerf.GetNullableIntDelegate", Setup, value => value.GetNullableIntDelegate());
        yield return Object<ReflectionPerf>("ReflectionPerf.GetNewIntDelegate", Setup, value => value.GetNewIntDelegate());
        yield return Object<ReflectionPerf>("ReflectionPerf.GetExistingIntDelegate", Setup, value => value.GetExistingIntDelegate());
        yield return Value<ReflectionPerf, string>("ReflectionPerf.CreateAndIterateList", Setup, value => value.CreateAndIterateList(), value => value.Length);
        yield return Object<ReflectionPerf>("ReflectionPerf.GetUri", Setup, value => value.GetUri());
        yield return Void<ReflectionPerf>("ReflectionPerf.SetUri", Setup, value => value.SetUri());
        yield return Object<ReflectionPerf>("ReflectionPerf.GetExistingUri", Setup, value => value.GetExistingUri());
        yield return Object<ReflectionPerf>("ReflectionPerf.GetWinRTType", Setup, value => value.GetWinRTType());
        yield return Void<ReflectionPerf>("ReflectionPerf.SetWinRTType", Setup, value => value.SetWinRTType());
        yield return Void<ReflectionPerf>("ReflectionPerf.SetPrimitiveType", Setup, value => value.SetPrimitiveType());
        yield return Void<ReflectionPerf>("ReflectionPerf.SetNonWinRTType", Setup, value => value.SetNonWinRTType());
        yield return Object<ReflectionPerf>("ReflectionPerf.GetExistingWinRTType", Setup, value => value.GetExistingWinRTType());
        yield return Void<ReflectionPerf>("ReflectionPerf.GetWeakReferenceOfManagedObject", Setup, value => value.GetWeakReferenceOfManagedObject());
        yield return Object<ReflectionPerf>("ReflectionPerf.GetAndResolveWeakReferenceOfManagedObject", Setup, value => value.GetAndResolveWeakReferenceOfManagedObject());
        yield return Object<ReflectionPerf>("ReflectionPerf.GetWeakReferenceOfNativeObject", Setup, value => value.GetWeakReferenceOfNativeObject());

        static void Setup(ReflectionPerf value) => value.Setup();
    }

    private static IEnumerable<BenchmarkScenarioDefinition> AsyncDefinitions()
    {
        yield return AsyncVoid<AsyncPerf>("AsyncPerf.Complete", Setup, value => value.Complete());
        yield return AsyncVoid<AsyncPerf>("AsyncPerf.YieldComplete", Setup, value => value.YieldComplete());
        yield return AsyncValue<AsyncPerf, int>("AsyncPerf.Return", Setup, value => value.Return(), value => value);
        yield return AsyncValue<AsyncPerf, int>("AsyncPerf.YieldReturn", Setup, value => value.YieldReturn(), value => value);

        static void Setup(AsyncPerf value) => value.Setup();
    }

    private static IEnumerable<BenchmarkScenarioDefinition> NonAgileObjectDefinitions()
    {
        yield return Void<NonAgileObjectPerf>(
            "NonAgileObjectPerf.ConstructAndQueryNonAgileObject",
            value => value.Setup(),
            value => value.ConstructAndQueryNonAgileObject(),
            value => value.Cleanup());
        yield return Void<NonAgileObjectPerf>(
            "NonAgileObjectPerf.ConstructNonAgileObject",
            value => value.Setup(),
            value => value.ConstructNonAgileObject(),
            value => value.Cleanup());
    }

    private static BenchmarkScenarioDefinition Object<TBenchmark>(
        string name,
        Action<TBenchmark>? setup,
        Func<TBenchmark, object?> invoke,
        Action<TBenchmark>? cleanup = null)
        where TBenchmark : new() =>
        Value(
            name,
            setup,
            invoke,
            value =>
            {
                Volatile.Write(ref objectResultSink, value);
                return value is null ? 0 : 1;
            },
            cleanup);

    private static BenchmarkScenarioDefinition Value<TBenchmark, TResult>(
        string name,
        Action<TBenchmark>? setup,
        Func<TBenchmark, TResult> invoke,
        Func<TResult, long> checksum,
        Action<TBenchmark>? cleanup = null)
        where TBenchmark : new() =>
        new(
            name,
            () =>
            {
                var benchmark = new TBenchmark();
                setup?.Invoke(benchmark);
                return new PreparedBenchmarkScenario(
                    name,
                    null,
                    iterations =>
                    {
                        long result = 0;
                        for (int index = 0; index < iterations; index++)
                        {
                            result += checksum(invoke(benchmark));
                        }
                        return result;
                    },
                    cleanup is null ? null : () => cleanup(benchmark));
            });

    private static BenchmarkScenarioDefinition Void<TBenchmark>(
        string name,
        Action<TBenchmark>? setup,
        Action<TBenchmark> invoke,
        Action<TBenchmark>? cleanup = null)
        where TBenchmark : new() =>
        new(
            name,
            () =>
            {
                var benchmark = new TBenchmark();
                setup?.Invoke(benchmark);
                return new PreparedBenchmarkScenario(
                    name,
                    1,
                    iterations =>
                    {
                        for (int index = 0; index < iterations; index++)
                        {
                            invoke(benchmark);
                        }
                        return iterations;
                    },
                    cleanup is null ? null : () => cleanup(benchmark));
            });

    private static BenchmarkScenarioDefinition AsyncVoid<TBenchmark>(
        string name,
        Action<TBenchmark>? setup,
        Func<TBenchmark, Task> invoke)
        where TBenchmark : new() =>
        new(
            name,
            () =>
            {
                var benchmark = new TBenchmark();
                setup?.Invoke(benchmark);
                return new PreparedBenchmarkScenario(
                    name,
                    1,
                    iterations =>
                    {
                        for (int index = 0; index < iterations; index++)
                        {
                            invoke(benchmark).GetAwaiter().GetResult();
                        }
                        return iterations;
                    });
            });

    private static BenchmarkScenarioDefinition AsyncValue<TBenchmark, TResult>(
        string name,
        Action<TBenchmark>? setup,
        Func<TBenchmark, Task<TResult>> invoke,
        Func<TResult, long> checksum)
        where TBenchmark : new() =>
        new(
            name,
            () =>
            {
                var benchmark = new TBenchmark();
                setup?.Invoke(benchmark);
                return new PreparedBenchmarkScenario(
                    name,
                    null,
                    iterations =>
                    {
                        long result = 0;
                        for (int index = 0; index < iterations; index++)
                        {
                            result += checksum(invoke(benchmark).GetAwaiter().GetResult());
                        }
                        return result;
                    });
            });
}
