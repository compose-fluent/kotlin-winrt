#include "ReferenceScenarios.h"

#include <winrt/BenchmarkComponent.h>
#include <winrt/Windows.ApplicationModel.Chat.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.Storage.h>
#include <winrt/Windows.System.Power.h>
#include <winrt/Windows.UI.Popups.h>
#include <winrt/Windows.UI.Xaml.Interop.h>

#include <Windows.h>
#include <objbase.h>

#include <chrono>
#include <cstdint>
#include <exception>
#include <memory>
#include <optional>
#include <stdexcept>
#include <string>
#include <thread>
#include <utility>
#include <vector>

namespace benchmark
{
    namespace
    {
        using namespace winrt;
        using namespace winrt::BenchmarkComponent;
        using namespace winrt::Windows::Foundation;
        using namespace winrt::Windows::Foundation::Collections;

        volatile std::uint8_t object_result_sink{};

        template <typename TContext, typename TSetup, typename TOperation, typename TCleanup>
        benchmark_scenario scenario(
            std::string name,
            std::optional<std::uint64_t> expected_single_checksum,
            TSetup setup,
            TOperation operation,
            TCleanup cleanup)
        {
            return benchmark_scenario{
                std::move(name),
                expected_single_checksum,
                [setup, operation, cleanup]
                {
                    auto context = std::make_shared<TContext>();
                    setup(*context);
                    return prepared_benchmark_scenario{
                        [context, operation](int iterations)
                        {
                            std::uint64_t checksum{};
                            for (int index = 0; index < iterations; ++index)
                            {
                                checksum += operation(*context);
                            }
                            return checksum;
                        },
                        [context, cleanup]
                        {
                            cleanup(*context);
                        },
                    };
                },
            };
        }

        template <typename TContext, typename TSetup, typename TOperation>
        benchmark_scenario scenario(
            std::string name,
            std::optional<std::uint64_t> expected_single_checksum,
            TSetup setup,
            TOperation operation)
        {
            return scenario<TContext>(
                std::move(name),
                expected_single_checksum,
                setup,
                operation,
                [](TContext&) {});
        }

        template <typename TValue>
        std::uint64_t object_checksum(TValue const& value)
        {
            if constexpr (requires { static_cast<bool>(value); })
            {
                return static_cast<bool>(value) ? std::uint64_t{ 1 } : std::uint64_t{};
            }
            else
            {
                object_result_sink = *reinterpret_cast<std::uint8_t const*>(std::addressof(value));
                return std::uint64_t{ 1 };
            }
        }

        template <typename TContext, typename TSetup, typename TInvoke>
        benchmark_scenario object_scenario(std::string name, TSetup setup, TInvoke invoke)
        {
            return scenario<TContext>(
                std::move(name),
                std::nullopt,
                setup,
                [invoke](TContext& context)
                {
                    auto value = invoke(context);
                    return object_checksum(value);
                });
        }

        template <typename TContext, typename TSetup, typename TInvoke>
        benchmark_scenario int_scenario(std::string name, TSetup setup, TInvoke invoke)
        {
            return scenario<TContext>(
                std::move(name),
                std::nullopt,
                setup,
                [invoke](TContext& context)
                {
                    return static_cast<std::uint64_t>(invoke(context));
                });
        }

        template <typename TContext, typename TSetup, typename TInvoke>
        benchmark_scenario bool_scenario(std::string name, TSetup setup, TInvoke invoke)
        {
            return scenario<TContext>(
                std::move(name),
                std::nullopt,
                setup,
                [invoke](TContext& context)
                {
                    return invoke(context) ? std::uint64_t{ 1 } : std::uint64_t{};
                });
        }

        template <typename TContext, typename TSetup, typename TInvoke>
        benchmark_scenario void_scenario(std::string name, TSetup setup, TInvoke invoke)
        {
            return scenario<TContext>(
                std::move(name),
                std::uint64_t{ 1 },
                setup,
                [invoke](TContext& context)
                {
                    invoke(context);
                    return std::uint64_t{ 1 };
                });
        }

        struct ManagedObjectWithInterfaces :
            implements<ManagedObjectWithInterfaces, IIntProperties, IBoolProperties>
        {
            int32_t IntProperty() const noexcept
            {
                return int_property;
            }

            void IntProperty(int32_t value) noexcept
            {
                int_property = value;
            }

            bool BoolProperty() const noexcept
            {
                return bool_property;
            }

            void BoolProperty(bool value) noexcept
            {
                bool_property = value;
            }

        private:
            int32_t int_property{};
            bool bool_property{};
        };

        struct ManagedComposableObjectWithInterfaces :
            ComposableT<ManagedComposableObjectWithInterfaces, IIntProperties>
        {
            int32_t IntProperty() const noexcept
            {
                return int_property;
            }

            void IntProperty(int32_t value) noexcept
            {
                int_property = value;
            }

        private:
            int32_t int_property{};
        };

        class QueryInterfacePerf
        {
        public:
            void Setup()
            {
                instance = ClassWithMultipleInterfaces();
                message = Windows::ApplicationModel::Chat::ChatMessage();
                managed_object = make<ManagedObjectWithInterfaces>();
                managed_composable_object = make<ManagedComposableObjectWithInterfaces>()
                    .as<IIntProperties>();
                fast_abi_instance = ClassWithFastAbi();
                fast_abi_derived_instance = ClassWithFastAbiDerived();
            }

            int32_t QueryDefaultInterface() const { return instance.DefaultIntProperty(); }
            int32_t QueryNonDefaultInterface() const { return instance.IntProperty(); }
            int32_t QueryFastAbiDefaultInterface() const { return fast_abi_instance.DefaultIntProperty(); }
            int32_t QueryFastAbiNonDefaultInterface() const { return fast_abi_instance.NonDefaultIntProperty(); }
            int32_t QueryFastAbiDerivedDefaultInterface() const { return fast_abi_derived_instance.DerivedDefaultIntProperty(); }
            int32_t QueryFastAbiComposedNonDefaultInterface() const { return fast_abi_derived_instance.DerivedNonDefaultIntProperty(); }
            int32_t QueryFastAbiComposedBaseDefaultInterface() const { return fast_abi_derived_instance.DefaultIntProperty(); }
            int32_t QueryFastAbiComposedBaseNonDefaultInterface() const { return fast_abi_derived_instance.NonDefaultIntProperty(); }
            bool QueryNonDefaultInterface2() const { return instance.BoolProperty(); }

            void QueryDefaultInterfaceSetProperty() const { instance.DefaultIntProperty(4); }
            void QueryNonDefaultInterfaceSetProperty() const { instance.IntProperty(4); }
            bool QuerySDKDefaultInterface() const { return message.IsForwardingDisabled(); }
            bool QuerySDKNonDefaultInterface() const { return message.IsSeen(); }

            IInspectable DefaultObjectParameters() const
            {
                instance.DefaultObjectProperty(ClassWithMultipleInterfaces());
                return instance.DefaultObjectProperty();
            }

            hstring DefaultStringParameters() const
            {
                instance.DefaultStringProperty(L"Hello");
                return instance.DefaultStringProperty();
            }

            ClassWithMarshalingRoutines DynamicCast() const
            {
                return instance.NewObject().as<ClassWithMarshalingRoutines>();
            }

            int32_t ConstructAndQueryDefaultInterfaceFirstCall() const { return ClassWithMultipleInterfaces().DefaultIntProperty(); }
            int32_t ConstructAndQueryNonDefaultInterfaceFirstCall() const { return ClassWithMultipleInterfaces().IntProperty(); }
            int32_t ConstructAndQueryFastAbiDefaultInterfaceFirstCall() const { return ClassWithFastAbi().DefaultIntProperty(); }
            int32_t ConstructAndQueryFastAbiNonDefaultInterfaceFirstCall() const { return ClassWithFastAbi().NonDefaultIntProperty(); }
            int32_t ConstructAndQueryFastAbiDerivedDefaultInterfaceFirstCall() const { return ClassWithFastAbiDerived().DerivedDefaultIntProperty(); }
            int32_t ConstructAndQueryFastAbiDerivedNonDefaultInterfaceFirstCall() const { return ClassWithFastAbiDerived().DerivedNonDefaultIntProperty(); }
            int32_t ConstructAndQueryFastAbiDerivedBaseDefaultInterfaceFirstCall() const { return ClassWithFastAbiDerived().DefaultIntProperty(); }
            int32_t ConstructAndQueryFastAbiDerivedBaseNonDefaultInterfaceFirstCall() const { return ClassWithFastAbiDerived().NonDefaultIntProperty(); }
            int32_t StaticPropertyCall() const { return Windows::System::Power::PowerManager::RemainingChargePercent(); }
            void QueryInterfaceOnManagedObject() const { instance.QueryBoolInterface(managed_object); }
            void QueryNativeInterfaceOnComposedObject() const { instance.QueryBoolInterface(managed_composable_object); }

        private:
            ClassWithMultipleInterfaces instance{ nullptr };
            Windows::ApplicationModel::Chat::ChatMessage message{ nullptr };
            IIntProperties managed_object{ nullptr };
            IIntProperties managed_composable_object{ nullptr };
            ClassWithFastAbi fast_abi_instance{ nullptr };
            ClassWithFastAbiDerived fast_abi_derived_instance{ nullptr };
        };

        std::vector<benchmark_scenario> query_interface_scenarios()
        {
            auto setup = [](QueryInterfacePerf& value) { value.Setup(); };
            return {
                int_scenario<QueryInterfacePerf>("QueryInterfacePerf.QueryDefaultInterface", setup, [](auto& value) { return value.QueryDefaultInterface(); }),
                int_scenario<QueryInterfacePerf>("QueryInterfacePerf.QueryNonDefaultInterface", setup, [](auto& value) { return value.QueryNonDefaultInterface(); }),
                int_scenario<QueryInterfacePerf>("QueryInterfacePerf.QueryFastAbiDefaultInterface", setup, [](auto& value) { return value.QueryFastAbiDefaultInterface(); }),
                int_scenario<QueryInterfacePerf>("QueryInterfacePerf.QueryFastAbiNonDefaultInterface", setup, [](auto& value) { return value.QueryFastAbiNonDefaultInterface(); }),
                int_scenario<QueryInterfacePerf>("QueryInterfacePerf.QueryFastAbiDerivedDefaultInterface", setup, [](auto& value) { return value.QueryFastAbiDerivedDefaultInterface(); }),
                int_scenario<QueryInterfacePerf>("QueryInterfacePerf.QueryFastAbiComposedNonDefaultInterface", setup, [](auto& value) { return value.QueryFastAbiComposedNonDefaultInterface(); }),
                int_scenario<QueryInterfacePerf>("QueryInterfacePerf.QueryFastAbiComposedBaseDefaultInterface", setup, [](auto& value) { return value.QueryFastAbiComposedBaseDefaultInterface(); }),
                int_scenario<QueryInterfacePerf>("QueryInterfacePerf.QueryFastAbiComposedBaseNonDefaultInterface", setup, [](auto& value) { return value.QueryFastAbiComposedBaseNonDefaultInterface(); }),
                bool_scenario<QueryInterfacePerf>("QueryInterfacePerf.QueryNonDefaultInterface2", setup, [](auto& value) { return value.QueryNonDefaultInterface2(); }),
                void_scenario<QueryInterfacePerf>("QueryInterfacePerf.QueryDefaultInterfaceSetProperty", setup, [](auto& value) { value.QueryDefaultInterfaceSetProperty(); }),
                void_scenario<QueryInterfacePerf>("QueryInterfacePerf.QueryNonDefaultInterfaceSetProperty", setup, [](auto& value) { value.QueryNonDefaultInterfaceSetProperty(); }),
                bool_scenario<QueryInterfacePerf>("QueryInterfacePerf.QuerySDKDefaultInterface", setup, [](auto& value) { return value.QuerySDKDefaultInterface(); }),
                bool_scenario<QueryInterfacePerf>("QueryInterfacePerf.QuerySDKNonDefaultInterface", setup, [](auto& value) { return value.QuerySDKNonDefaultInterface(); }),
                object_scenario<QueryInterfacePerf>("QueryInterfacePerf.DefaultObjectParameters", setup, [](auto& value) { return value.DefaultObjectParameters(); }),
                object_scenario<QueryInterfacePerf>("QueryInterfacePerf.DefaultStringParameters", setup, [](auto& value) { return value.DefaultStringParameters(); }),
                object_scenario<QueryInterfacePerf>("QueryInterfacePerf.DynamicCast", setup, [](auto& value) { return value.DynamicCast(); }),
                int_scenario<QueryInterfacePerf>("QueryInterfacePerf.ConstructAndQueryDefaultInterfaceFirstCall", setup, [](auto& value) { return value.ConstructAndQueryDefaultInterfaceFirstCall(); }),
                int_scenario<QueryInterfacePerf>("QueryInterfacePerf.ConstructAndQueryNonDefaultInterfaceFirstCall", setup, [](auto& value) { return value.ConstructAndQueryNonDefaultInterfaceFirstCall(); }),
                int_scenario<QueryInterfacePerf>("QueryInterfacePerf.ConstructAndQueryFastAbiDefaultInterfaceFirstCall", setup, [](auto& value) { return value.ConstructAndQueryFastAbiDefaultInterfaceFirstCall(); }),
                int_scenario<QueryInterfacePerf>("QueryInterfacePerf.ConstructAndQueryFastAbiNonDefaultInterfaceFirstCall", setup, [](auto& value) { return value.ConstructAndQueryFastAbiNonDefaultInterfaceFirstCall(); }),
                int_scenario<QueryInterfacePerf>("QueryInterfacePerf.ConstructAndQueryFastAbiDerivedDefaultInterfaceFirstCall", setup, [](auto& value) { return value.ConstructAndQueryFastAbiDerivedDefaultInterfaceFirstCall(); }),
                int_scenario<QueryInterfacePerf>("QueryInterfacePerf.ConstructAndQueryFastAbiDerivedNonDefaultInterfaceFirstCall", setup, [](auto& value) { return value.ConstructAndQueryFastAbiDerivedNonDefaultInterfaceFirstCall(); }),
                int_scenario<QueryInterfacePerf>("QueryInterfacePerf.ConstructAndQueryFastAbiDerivedBaseDefaultInterfaceFirstCall", setup, [](auto& value) { return value.ConstructAndQueryFastAbiDerivedBaseDefaultInterfaceFirstCall(); }),
                int_scenario<QueryInterfacePerf>("QueryInterfacePerf.ConstructAndQueryFastAbiDerivedBaseNonDefaultInterfaceFirstCall", setup, [](auto& value) { return value.ConstructAndQueryFastAbiDerivedBaseNonDefaultInterfaceFirstCall(); }),
                int_scenario<QueryInterfacePerf>("QueryInterfacePerf.StaticPropertyCall", setup, [](auto& value) { return value.StaticPropertyCall(); }),
                void_scenario<QueryInterfacePerf>("QueryInterfacePerf.QueryInterfaceOnManagedObject", setup, [](auto& value) { value.QueryInterfaceOnManagedObject(); }),
                void_scenario<QueryInterfacePerf>("QueryInterfacePerf.QueryNativeInterfaceOnComposedObject", setup, [](auto& value) { value.QueryNativeInterfaceOnComposedObject(); }),
            };
        }

        struct ManagedEvents : implements<ManagedEvents, IEvents>
        {
            event_token IntPropertyChanged(EventHandler<int32_t> const& handler)
            {
                return int_property_changed.add(handler);
            }

            void IntPropertyChanged(event_token const& token) noexcept
            {
                int_property_changed.remove(token);
            }

            event_token DoublePropertyChanged(EventHandler<double> const& handler)
            {
                return double_property_changed.add(handler);
            }

            void DoublePropertyChanged(event_token const& token) noexcept
            {
                double_property_changed.remove(token);
            }

            void RaiseIntChanged()
            {
                int_property_changed(*this, 4);
            }

            void RaiseDoubleChanged()
            {
                double_property_changed(*this, 2.2);
            }

        private:
            event<EventHandler<int32_t>> int_property_changed;
            event<EventHandler<double>> double_property_changed;
        };

        class EventPerf
        {
        public:
            void Setup()
            {
                instance = ClassWithMarshalingRoutines();
                instance.IntPropertyChanged([this](IInspectable const&, int32_t value) { z2 = value; });

                instance2 = ClassWithMarshalingRoutines();
                instance2.IntPropertyChanged([this](IInspectable const&, int32_t value) { z4 = value * 3; });

                events = make<ManagedEvents>();
                operations = EventOperations(events);
                operations.AddIntEvent();
            }

            ClassWithMarshalingRoutines IntEventOverhead() const
            {
                ClassWithMarshalingRoutines value;
                int32_t z{};
                EventHandler<int32_t> handler = [&z](IInspectable const&, int32_t event_value) { z = event_value; };
                return value;
            }

            ClassWithMarshalingRoutines AddIntEventToNewEventSource() const
            {
                ClassWithMarshalingRoutines value;
                int32_t z{};
                value.IntPropertyChanged([&z](IInspectable const&, int32_t event_value) { z = event_value; });
                return value;
            }

            ClassWithMarshalingRoutines AddMultipleEventsToNewEventSource() const
            {
                ClassWithMarshalingRoutines value;
                int32_t z{};
                double y{};
                value.IntPropertyChanged([&z](IInspectable const&, int32_t event_value) { z = event_value; });
                value.DoublePropertyChanged([&y](IInspectable const&, double event_value) { y = event_value; });
                return value;
            }

            ClassWithMarshalingRoutines AddAndInvokeIntEventOnNewEventSource() const
            {
                ClassWithMarshalingRoutines value;
                int32_t z{};
                value.IntPropertyChanged([&z](IInspectable const&, int32_t event_value) { z = event_value; });
                value.RaiseIntChanged();
                return value;
            }

            ClassWithMarshalingRoutines AddAndInvokeMultipleIntEventsToSameEventSource() const
            {
                ClassWithMarshalingRoutines value;
                int32_t z{};
                value.IntPropertyChanged([&z](IInspectable const&, int32_t event_value) { z = event_value; });
                value.IntPropertyChanged([&z](IInspectable const&, int32_t event_value) { z = event_value * 2; });
                value.IntPropertyChanged([&z](IInspectable const&, int32_t event_value) { z = event_value * 3; });
                value.RaiseIntChanged();
                return value;
            }

            int32_t InvokeIntEvent()
            {
                instance.RaiseIntChanged();
                return z2;
            }

            int32_t InvokeIntEventWithSenderCheck()
            {
                instance2.RaiseIntChanged();
                return z4;
            }

            ClassWithMarshalingRoutines AddAndRemoveIntEventOnNewEventSource() const
            {
                ClassWithMarshalingRoutines value;
                int32_t z{};
                EventHandler<int32_t> handler = [&z](IInspectable const&, int32_t event_value) { z = event_value; };
                event_token const token = value.IntPropertyChanged(handler);
                value.IntPropertyChanged(token);
                return value;
            }

            EventOperations NativeIntEventOverhead() const
            {
                return EventOperations(make<ManagedEvents>());
            }

            EventOperations AddNativeIntEventToNewEventSource() const
            {
                EventOperations value(make<ManagedEvents>());
                value.AddIntEvent();
                return value;
            }

            EventOperations AddMultipleNativeEventsToNewEventSource() const
            {
                EventOperations value(make<ManagedEvents>());
                value.AddIntEvent();
                value.AddDoubleEvent();
                return value;
            }

            EventOperations AddAndInvokeNativeIntEventOnNewEventSource() const
            {
                EventOperations value(make<ManagedEvents>());
                value.AddIntEvent();
                value.FireIntEvent();
                return value;
            }

            void InvokeNativeIntEvent() const
            {
                operations.FireIntEvent();
            }

            EventOperations AddAndRemoveNativeIntEventOnNewEventSource() const
            {
                EventOperations value(make<ManagedEvents>());
                value.AddIntEvent();
                value.RemoveIntEvent();
                return value;
            }

        private:
            ClassWithMarshalingRoutines instance{ nullptr };
            int32_t z2{};
            ClassWithMarshalingRoutines instance2{ nullptr };
            int32_t z4{};
            IEvents events{ nullptr };
            EventOperations operations{ nullptr };
        };

        std::vector<benchmark_scenario> event_scenarios()
        {
            auto setup = [](EventPerf& value) { value.Setup(); };
            return {
                object_scenario<EventPerf>("EventPerf.IntEventOverhead", setup, [](auto& value) { return value.IntEventOverhead(); }),
                object_scenario<EventPerf>("EventPerf.AddIntEventToNewEventSource", setup, [](auto& value) { return value.AddIntEventToNewEventSource(); }),
                object_scenario<EventPerf>("EventPerf.AddMultipleEventsToNewEventSource", setup, [](auto& value) { return value.AddMultipleEventsToNewEventSource(); }),
                object_scenario<EventPerf>("EventPerf.AddAndInvokeIntEventOnNewEventSource", setup, [](auto& value) { return value.AddAndInvokeIntEventOnNewEventSource(); }),
                object_scenario<EventPerf>("EventPerf.AddAndInvokeMultipleIntEventsToSameEventSource", setup, [](auto& value) { return value.AddAndInvokeMultipleIntEventsToSameEventSource(); }),
                int_scenario<EventPerf>("EventPerf.InvokeIntEvent", setup, [](auto& value) { return value.InvokeIntEvent(); }),
                int_scenario<EventPerf>("EventPerf.InvokeIntEventWithSenderCheck", setup, [](auto& value) { return value.InvokeIntEventWithSenderCheck(); }),
                object_scenario<EventPerf>("EventPerf.AddAndRemoveIntEventOnNewEventSource", setup, [](auto& value) { return value.AddAndRemoveIntEventOnNewEventSource(); }),
                object_scenario<EventPerf>("EventPerf.NativeIntEventOverhead", setup, [](auto& value) { return value.NativeIntEventOverhead(); }),
                object_scenario<EventPerf>("EventPerf.AddNativeIntEventToNewEventSource", setup, [](auto& value) { return value.AddNativeIntEventToNewEventSource(); }),
                object_scenario<EventPerf>("EventPerf.AddMultipleNativeEventsToNewEventSource", setup, [](auto& value) { return value.AddMultipleNativeEventsToNewEventSource(); }),
                object_scenario<EventPerf>("EventPerf.AddAndInvokeNativeIntEventOnNewEventSource", setup, [](auto& value) { return value.AddAndInvokeNativeIntEventOnNewEventSource(); }),
                void_scenario<EventPerf>("EventPerf.InvokeNativeIntEvent", setup, [](auto& value) { value.InvokeNativeIntEvent(); }),
                object_scenario<EventPerf>("EventPerf.AddAndRemoveNativeIntEventOnNewEventSource", setup, [](auto& value) { return value.AddAndRemoveNativeIntEventOnNewEventSource(); }),
            };
        }

        class GuidPerf
        {
        public:
            guid GetClassGuid() const { return guid_of<ClassWithMarshalingRoutines>(); }
            guid GetDelegateGuid() const { return guid_of<AsyncActionCompletedHandler>(); }
            guid CreateListGuid() const { return guid_of<IVector<ClassWithMarshalingRoutines>>(); }
            guid CreateDictionaryWithStringKeyGuid() const { return guid_of<IMap<hstring, ClassWithMarshalingRoutines>>(); }
            guid CreateDictionaryWithBoolKeyGuid() const { return guid_of<IMap<bool, ClassWithMarshalingRoutines>>(); }
            guid CreateReadOnlyEnumListGuid() const { return guid_of<IVectorView<AsyncStatus>>(); }
            guid CreateReadOnlyFlagEnumListGuid() const { return guid_of<IVectorView<Windows::Storage::FileAttributes>>(); }
            guid CreateReadOnlyStructListGuid() const { return guid_of<IVectorView<NonBlittable>>(); }
            guid CreateReadOnlyInterfaceListGuid() const { return guid_of<IVectorView<IIntProperties>>(); }
            guid CreateReadOnlyClassListGuid() const { return guid_of<IVectorView<EventOperations>>(); }
            guid CreateReadOnlyDelegateListGuid() const { return guid_of<IVectorView<ProvideInt>>(); }
        };

        std::vector<benchmark_scenario> guid_scenarios()
        {
            auto setup = [](GuidPerf&) {};
            return {
                object_scenario<GuidPerf>("GuidPerf.GetClassGuid", setup, [](auto& value) { return value.GetClassGuid(); }),
                object_scenario<GuidPerf>("GuidPerf.GetDelegateGuid", setup, [](auto& value) { return value.GetDelegateGuid(); }),
                object_scenario<GuidPerf>("GuidPerf.CreateListGuid", setup, [](auto& value) { return value.CreateListGuid(); }),
                object_scenario<GuidPerf>("GuidPerf.CreateDictionaryWithStringKeyGuid", setup, [](auto& value) { return value.CreateDictionaryWithStringKeyGuid(); }),
                object_scenario<GuidPerf>("GuidPerf.CreateDictionaryWithBoolKeyGuid", setup, [](auto& value) { return value.CreateDictionaryWithBoolKeyGuid(); }),
                object_scenario<GuidPerf>("GuidPerf.CreateReadOnlyEnumListGuid", setup, [](auto& value) { return value.CreateReadOnlyEnumListGuid(); }),
                object_scenario<GuidPerf>("GuidPerf.CreateReadOnlyFlagEnumListGuid", setup, [](auto& value) { return value.CreateReadOnlyFlagEnumListGuid(); }),
                object_scenario<GuidPerf>("GuidPerf.CreateReadOnlyStructListGuid", setup, [](auto& value) { return value.CreateReadOnlyStructListGuid(); }),
                object_scenario<GuidPerf>("GuidPerf.CreateReadOnlyInterfaceListGuid", setup, [](auto& value) { return value.CreateReadOnlyInterfaceListGuid(); }),
                object_scenario<GuidPerf>("GuidPerf.CreateReadOnlyClassListGuid", setup, [](auto& value) { return value.CreateReadOnlyClassListGuid(); }),
                object_scenario<GuidPerf>("GuidPerf.CreateReadOnlyDelegateListGuid", setup, [](auto& value) { return value.CreateReadOnlyDelegateListGuid(); }),
            };
        }

        class ReflectionPerf
        {
        public:
            void Setup()
            {
                instance = ClassWithMarshalingRoutines();
                instance_dictionary = instance.ExistingDictionary();
                managed_object = make<ManagedObjectWithInterfaces>();
            }

            IInspectable ExecuteMarshalingForNewKeyValuePair() const { return instance.NewTypeErasedKeyValuePairObject(); }
            IInspectable ExecuteMarshalingForNewArray() const { return instance.NewTypeErasedArrayObject(); }
            IInspectable ExecuteMarshalingForNewNullable() const { return instance.NewTypeErasedNullableObject(); }
            IInspectable ExecuteMarshalingForExistingKeyvaluePair() const { return instance.ExistingTypeErasedKeyValuePairObject(); }
            IInspectable ExecuteMarshalingForExistingArray() const { return instance.ExistingTypeErasedArrayObject(); }
            IInspectable ExecuteMarshalingForExistingNullable() const { return instance.ExistingTypeErasedNullableObject(); }
            hstring ExecuteMarshalingForString() const { return instance.DefaultStringProperty(); }
            WrappedClass ExecuteMarshalingForCustomObject() const { return instance.NewWrappedClassObject(); }

            int32_t ExecuteMarshalingForDelegate() const
            {
                int32_t const y = 1;
                instance.CallForInt([y] { return y; });
                return y;
            }

            void IntEventSource() const
            {
                ClassWithMarshalingRoutines value;
                int32_t const x{};
                int32_t const y = 1;
                int32_t z{};
                value.IntProperty(x);
                value.CallForInt([y] { return y; });
                value.IntPropertyChanged([&z](IInspectable const&, int32_t event_value) { z = event_value; });
                value.RaiseIntChanged();
            }

            int32_t ExistingDictionaryLookup() const
            {
                auto dictionary = instance.ExistingDictionary();
                int32_t count{};
                for (int index = 0; index < 100; ++index)
                {
                    if (dictionary.Lookup(L"a"))
                    {
                        ++count;
                    }
                }
                return count;
            }

            WrappedClass ExistingDictionaryLookupCached() const
            {
                auto dictionary = instance.ExistingDictionary();
                WrappedClass cache{ nullptr };
                for (int index = 0; index < 1'000; ++index)
                {
                    cache = dictionary.Lookup(L"a");
                }
                return cache;
            }

            WrappedClass ExistingDictionaryLookup2() const { return instance_dictionary.Lookup(L"a"); }
            WrappedClass ExistingDictionaryLookup3() const { return instance.ExistingDictionary().Lookup(L"a"); }
            int32_t GetNullableInt() const { return instance.NullableInt().Value(); }
            void SetNullableInt() const { instance.NullableInt(box_value(4).as<IReference<int32_t>>()); }
            BlittableStruct GetNullableBittableStruct() const { return instance.NullableBlittableStruct().Value(); }

            void SetNullableBittableStruct() const
            {
                instance.NullableBlittableStruct(
                    box_value(BlittableStruct{ 2 }).as<IReference<BlittableStruct>>());
            }

            TimeSpan GetNullableTimeSpan() const { return instance.NullableTimeSpan().Value(); }

            void SetNullableTimeSpan() const
            {
                instance.NullableTimeSpan(box_value(TimeSpan{ 100 }).as<IReference<TimeSpan>>());
            }

            NonBlittable GetNullableNonBittableStruct() const { return instance.NullableNonBlittableStruct().Value(); }

            void SetNullableNonBittableStruct() const
            {
                instance.NullableNonBlittableStruct(
                    box_value(NonBlittable{ true, L"beta" }).as<IReference<NonBlittable>>());
            }

            void SetNullableDelegate() const
            {
                int32_t z{};
                EventHandler<int32_t> handler = [&z](IInspectable const&, int32_t value) { z = value; };
                instance.NewTypeErasedNullableObject(box_value(handler));
            }

            void SetNullableIntDelegate() const
            {
                instance.BoxedDelegate(box_value(ProvideInt{ [] { return 4; } }));
            }

            ProvideInt GetNullableIntDelegate() const
            {
                return instance.BoxedDelegate().as<IReference<ProvideInt>>().Value();
            }

            ProvideInt GetNewIntDelegate() const { return instance.NewIntDelegate(); }
            ProvideInt GetExistingIntDelegate() const { return instance.ExistingIntDelegate(); }

            hstring CreateAndIterateList() const
            {
                auto list = instance.NewList();
                list.Append(L"How");
                list.Append(L"Are");
                list.Append(L"You");
                hstring sentence;
                for (std::uint32_t index = 0; index < list.Size(); ++index)
                {
                    sentence = sentence + list.GetAt(index);
                }
                return sentence;
            }

            Uri GetUri() const { return instance.NewUri(); }
            void SetUri() const { instance.NewUri(Uri(L"https://github.com")); }
            Uri GetExistingUri() const { return instance.ExistingUri(); }
            Windows::UI::Xaml::Interop::TypeName GetWinRTType() const { return instance.NewType(); }

            void SetWinRTType() const
            {
                instance.NewType({ L"BenchmarkComponent.ClassWithMarshalingRoutines", Windows::UI::Xaml::Interop::TypeKind::Metadata });
            }

            void SetPrimitiveType() const
            {
                instance.NewType({ L"Int32", Windows::UI::Xaml::Interop::TypeKind::Primitive });
            }

            void SetNonWinRTType() const
            {
                instance.NewType({ L"CppWinRTBenchmark.ReflectionPerf", Windows::UI::Xaml::Interop::TypeKind::Custom });
            }

            Windows::UI::Xaml::Interop::TypeName GetExistingWinRTType() const { return instance.ExistingType(); }
            void GetWeakReferenceOfManagedObject() const { instance.GetWeakReference(managed_object); }
            IInspectable GetAndResolveWeakReferenceOfManagedObject() const { return instance.GetAndResolveWeakReference(managed_object); }
            weak_ref<ClassWithMarshalingRoutines> GetWeakReferenceOfNativeObject() const { return make_weak(instance); }

        private:
            ClassWithMarshalingRoutines instance{ nullptr };
            IMap<hstring, WrappedClass> instance_dictionary{ nullptr };
            IIntProperties managed_object{ nullptr };
        };

        std::vector<benchmark_scenario> reflection_scenarios()
        {
            auto setup = [](ReflectionPerf& value) { value.Setup(); };
            return {
                object_scenario<ReflectionPerf>("ReflectionPerf.ExecuteMarshalingForNewKeyValuePair", setup, [](auto& value) { return value.ExecuteMarshalingForNewKeyValuePair(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.ExecuteMarshalingForNewArray", setup, [](auto& value) { return value.ExecuteMarshalingForNewArray(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.ExecuteMarshalingForNewNullable", setup, [](auto& value) { return value.ExecuteMarshalingForNewNullable(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.ExecuteMarshalingForExistingKeyvaluePair", setup, [](auto& value) { return value.ExecuteMarshalingForExistingKeyvaluePair(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.ExecuteMarshalingForExistingArray", setup, [](auto& value) { return value.ExecuteMarshalingForExistingArray(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.ExecuteMarshalingForExistingNullable", setup, [](auto& value) { return value.ExecuteMarshalingForExistingNullable(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.ExecuteMarshalingForString", setup, [](auto& value) { return value.ExecuteMarshalingForString(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.ExecuteMarshalingForCustomObject", setup, [](auto& value) { return value.ExecuteMarshalingForCustomObject(); }),
                int_scenario<ReflectionPerf>("ReflectionPerf.ExecuteMarshalingForDelegate", setup, [](auto& value) { return value.ExecuteMarshalingForDelegate(); }),
                void_scenario<ReflectionPerf>("ReflectionPerf.IntEventSource", setup, [](auto& value) { value.IntEventSource(); }),
                int_scenario<ReflectionPerf>("ReflectionPerf.ExistingDictionaryLookup", setup, [](auto& value) { return value.ExistingDictionaryLookup(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.ExistingDictionaryLookupCached", setup, [](auto& value) { return value.ExistingDictionaryLookupCached(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.ExistingDictionaryLookup2", setup, [](auto& value) { return value.ExistingDictionaryLookup2(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.ExistingDictionaryLookup3", setup, [](auto& value) { return value.ExistingDictionaryLookup3(); }),
                int_scenario<ReflectionPerf>("ReflectionPerf.GetNullableInt", setup, [](auto& value) { return value.GetNullableInt(); }),
                void_scenario<ReflectionPerf>("ReflectionPerf.SetNullableInt", setup, [](auto& value) { value.SetNullableInt(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.GetNullableBittableStruct", setup, [](auto& value) { return value.GetNullableBittableStruct(); }),
                void_scenario<ReflectionPerf>("ReflectionPerf.SetNullableBittableStruct", setup, [](auto& value) { value.SetNullableBittableStruct(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.GetNullableTimeSpan", setup, [](auto& value) { return value.GetNullableTimeSpan(); }),
                void_scenario<ReflectionPerf>("ReflectionPerf.SetNullableTimeSpan", setup, [](auto& value) { value.SetNullableTimeSpan(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.GetNullableNonBittableStruct", setup, [](auto& value) { return value.GetNullableNonBittableStruct(); }),
                void_scenario<ReflectionPerf>("ReflectionPerf.SetNullableNonBittableStruct", setup, [](auto& value) { value.SetNullableNonBittableStruct(); }),
                void_scenario<ReflectionPerf>("ReflectionPerf.SetNullableDelegate", setup, [](auto& value) { value.SetNullableDelegate(); }),
                void_scenario<ReflectionPerf>("ReflectionPerf.SetNullableIntDelegate", setup, [](auto& value) { value.SetNullableIntDelegate(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.GetNullableIntDelegate", setup, [](auto& value) { return value.GetNullableIntDelegate(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.GetNewIntDelegate", setup, [](auto& value) { return value.GetNewIntDelegate(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.GetExistingIntDelegate", setup, [](auto& value) { return value.GetExistingIntDelegate(); }),
                scenario<ReflectionPerf>("ReflectionPerf.CreateAndIterateList", std::nullopt, setup, [](auto& value) { return static_cast<std::uint64_t>(value.CreateAndIterateList().size()); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.GetUri", setup, [](auto& value) { return value.GetUri(); }),
                void_scenario<ReflectionPerf>("ReflectionPerf.SetUri", setup, [](auto& value) { value.SetUri(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.GetExistingUri", setup, [](auto& value) { return value.GetExistingUri(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.GetWinRTType", setup, [](auto& value) { return value.GetWinRTType(); }),
                void_scenario<ReflectionPerf>("ReflectionPerf.SetWinRTType", setup, [](auto& value) { value.SetWinRTType(); }),
                void_scenario<ReflectionPerf>("ReflectionPerf.SetPrimitiveType", setup, [](auto& value) { value.SetPrimitiveType(); }),
                void_scenario<ReflectionPerf>("ReflectionPerf.SetNonWinRTType", setup, [](auto& value) { value.SetNonWinRTType(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.GetExistingWinRTType", setup, [](auto& value) { return value.GetExistingWinRTType(); }),
                void_scenario<ReflectionPerf>("ReflectionPerf.GetWeakReferenceOfManagedObject", setup, [](auto& value) { value.GetWeakReferenceOfManagedObject(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.GetAndResolveWeakReferenceOfManagedObject", setup, [](auto& value) { return value.GetAndResolveWeakReferenceOfManagedObject(); }),
                object_scenario<ReflectionPerf>("ReflectionPerf.GetWeakReferenceOfNativeObject", setup, [](auto& value) { return value.GetWeakReferenceOfNativeObject(); }),
            };
        }

        class AsyncPerf
        {
        public:
            void Setup()
            {
                instance = ClassWithAsync();
            }

            void Complete() const { instance.Complete().get(); }
            void YieldComplete() const { instance.YieldComplete().get(); }
            int32_t Return() const { return instance.Return(5).get(); }
            int32_t YieldReturn() const { return instance.YieldReturn(5).get(); }

        private:
            ClassWithAsync instance{ nullptr };
        };

        std::vector<benchmark_scenario> async_scenarios()
        {
            auto setup = [](AsyncPerf& value) { value.Setup(); };
            return {
                void_scenario<AsyncPerf>("AsyncPerf.Complete", setup, [](auto& value) { value.Complete(); }),
                void_scenario<AsyncPerf>("AsyncPerf.YieldComplete", setup, [](auto& value) { value.YieldComplete(); }),
                int_scenario<AsyncPerf>("AsyncPerf.Return", setup, [](auto& value) { return value.Return(); }),
                int_scenario<AsyncPerf>("AsyncPerf.YieldReturn", setup, [](auto& value) { return value.YieldReturn(); }),
            };
        }

        class NonAgileObjectPerf
        {
        public:
            ~NonAgileObjectPerf()
            {
                Cleanup();
            }

            void Setup()
            {
                create_object = create_auto_reset_event();
                exit_thread = create_auto_reset_event();
                object_created = create_auto_reset_event();
                sta_thread = std::thread([this]
                {
                    try
                    {
                        ObjectAllocationLoop();
                    }
                    catch (hresult_error const& error)
                    {
                        thread_failure = thread_stage + ": " + to_string(error.message());
                        SetEvent(object_created);
                    }
                    catch (...)
                    {
                        thread_exception = std::current_exception();
                        SetEvent(object_created);
                    }
                });
            }

            void Cleanup() noexcept
            {
                if (sta_thread.joinable())
                {
                    SetEvent(exit_thread);
                    SetEvent(create_object);
                    sta_thread.join();
                }
                close_handle(object_created);
                close_handle(exit_thread);
                close_handle(create_object);
            }

            void ConstructAndQueryNonAgileObject()
            {
                ConstructNonAgileObject();
                CallObject();
            }

            void ConstructNonAgileObject()
            {
                char const* stage = "signal object creation";
                try
                {
                    ++construction_calls;
                    if (!SetEvent(create_object))
                    {
                        throw std::runtime_error(
                            "signal object creation call " + std::to_string(construction_calls) +
                            " failed for handle " + std::to_string(reinterpret_cast<std::uintptr_t>(create_object)) +
                            " with Win32 error " + std::to_string(GetLastError()));
                    }
                    stage = "wait for object creation";
                    check_wait(WaitForSingleObject(object_created, INFINITE));
                    stage = "reset object-created event";
                    check_bool(ResetEvent(object_created));
                    stage = "rethrow worker failure";
                    if (thread_exception)
                    {
                        std::rethrow_exception(thread_exception);
                    }
                    if (!thread_failure.empty())
                    {
                        throw std::runtime_error(thread_failure);
                    }
                }
                catch (hresult_error const& error)
                {
                    throw std::runtime_error(
                        std::string(stage) + ": " + to_string(error.message()));
                }
            }

        private:
            static HANDLE create_auto_reset_event()
            {
                HANDLE const value = CreateEventW(nullptr, FALSE, FALSE, nullptr);
                if (!value)
                {
                    throw_last_error();
                }
                return value;
            }

            static void check_wait(DWORD result)
            {
                if (result != WAIT_OBJECT_0)
                {
                    if (result == WAIT_FAILED)
                    {
                        throw_last_error();
                    }
                    throw std::runtime_error("Unexpected Win32 wait result.");
                }
            }

            static void wait_with_com_dispatch(HANDLE value)
            {
                DWORD signaled_index{};
                check_hresult(CoWaitForMultipleHandles(
                    static_cast<DWORD>(COWAIT_DISPATCH_CALLS | COWAIT_DISPATCH_WINDOW_MESSAGES),
                    INFINITE,
                    1,
                    &value,
                    &signaled_index));
                if (signaled_index != 0)
                {
                    throw std::runtime_error("CoWaitForMultipleHandles returned an unexpected handle index.");
                }
            }

            static void close_handle(HANDLE& value) noexcept
            {
                if (value)
                {
                    CloseHandle(value);
                    value = nullptr;
                }
            }

            void ObjectAllocationLoop()
            {
                thread_stage = "initialize STA apartment";
                init_apartment(apartment_type::single_threaded);
                while (true)
                {
                    thread_stage = "wait for object creation with COM dispatch";
                    wait_with_com_dispatch(create_object);
                    if (WaitForSingleObject(exit_thread, 1) == WAIT_OBJECT_0)
                    {
                        break;
                    }
                    ResetEvent(create_object);
                    thread_stage = "construct PopupMenu";
                    non_agile_object = Windows::UI::Popups::PopupMenu();
                    thread_stage = "query PopupMenu.Commands on STA";
                    CallObject();
                    thread_stage = "signal object creation";
                    check_bool(SetEvent(object_created));
                }
                non_agile_object = nullptr;
            }

            int32_t CallObject() const
            {
                return static_cast<int32_t>(non_agile_object.Commands().Size());
            }

            HANDLE create_object{};
            HANDLE exit_thread{};
            HANDLE object_created{};
            std::thread sta_thread;
            std::exception_ptr thread_exception;
            std::string thread_stage;
            std::string thread_failure;
            int construction_calls{};
            Windows::UI::Popups::PopupMenu non_agile_object{ nullptr };
        };

        std::vector<benchmark_scenario> non_agile_object_scenarios()
        {
            auto setup = [](NonAgileObjectPerf& value) { value.Setup(); };
            auto cleanup = [](NonAgileObjectPerf& value) { value.Cleanup(); };
            return {
                scenario<NonAgileObjectPerf>(
                    "NonAgileObjectPerf.ConstructAndQueryNonAgileObject",
                    std::uint64_t{ 1 },
                    setup,
                    [](auto& value)
                    {
                        value.ConstructAndQueryNonAgileObject();
                        return std::uint64_t{ 1 };
                    },
                    cleanup),
                scenario<NonAgileObjectPerf>(
                    "NonAgileObjectPerf.ConstructNonAgileObject",
                    std::uint64_t{ 1 },
                    setup,
                    [](auto& value)
                    {
                        value.ConstructNonAgileObject();
                        return std::uint64_t{ 1 };
                    },
                    cleanup),
            };
        }
    }

    std::vector<benchmark_scenario> reference_scenarios()
    {
        std::vector<benchmark_scenario> result;
        auto append = [&result](std::vector<benchmark_scenario> values)
        {
            result.insert(
                result.end(),
                std::make_move_iterator(values.begin()),
                std::make_move_iterator(values.end()));
        };
        append(query_interface_scenarios());
        append(event_scenarios());
        append(guid_scenarios());
        append(reflection_scenarios());
        append(async_scenarios());
        append(non_agile_object_scenarios());
        return result;
    }
}
