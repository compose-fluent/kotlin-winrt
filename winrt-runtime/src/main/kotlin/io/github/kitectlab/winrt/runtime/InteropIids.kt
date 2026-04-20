package io.github.kitectlab.winrt.runtime

object IID {
    val IUnknown: Guid = guidOf("00000000-0000-0000-C000-000000000046")
    val IInspectable: Guid = guidOf("AF86E2E0-B12D-4C6A-9C5A-D7AA65101E90")
    val IWeakReference: Guid = guidOf("00000037-0000-0000-C000-000000000046")
    val IWeakReferenceSource: Guid = guidOf("00000038-0000-0000-C000-000000000046")
    val IReferenceTracker: Guid = guidOf("11D3B13A-180E-4789-A8BE-7712882893E6")
    val IReferenceTrackerTarget: Guid = guidOf("64BD43F8-BFEE-4EC4-B7EB-2935158DAE21")
    val IActivationFactory: Guid = guidOf("00000035-0000-0000-C000-000000000046")
    val IAgileObject: Guid = guidOf("94EA2B94-E9CC-49E0-C0FF-EE64CA8F5B90")
    val IMarshal: Guid = guidOf("00000003-0000-0000-C000-000000000046")
    val IAgileReference: Guid = guidOf("C03F6A43-65A4-9818-987E-E0B810D2A6F2")
    val IContextCallback: Guid = guidOf("000001DA-0000-0000-C000-000000000046")
    val ICallbackWithNoReentrancyToApplicationSTA: Guid = guidOf("0A299774-3E4E-FC42-1D9D-72CEE105CA57")
    val IErrorInfo: Guid = guidOf("1CF2B120-547D-101B-8E65-08002B2BD119")
    val ILanguageExceptionErrorInfo: Guid = guidOf("04A2DBF3-DF83-116C-0946-0812ABF6E07D")
    val ILanguageExceptionErrorInfo2: Guid = guidOf("5746E5C4-5B97-424C-B620-2822915734DD")
    val IRestrictedErrorInfo: Guid = guidOf("82BA7092-4C88-427D-A7BC-16DD93FEB67E")
    val IGlobalInterfaceTable: Guid = guidOf("00000146-0000-0000-C000-000000000046")
}
