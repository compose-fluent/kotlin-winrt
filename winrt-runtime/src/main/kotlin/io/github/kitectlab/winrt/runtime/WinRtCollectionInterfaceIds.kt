package io.github.kitectlab.winrt.runtime

object WinRtCollectionInterfaceIds {
    val iKeyValuePair: Guid = Guid("02B51929-C1C4-4A7E-8940-0312B5C18500")
    val iIterable: Guid = Guid("FAA585EA-6214-4217-AFDA-7F46DE5869B3")
    val iVectorView: Guid = Guid("BBE1FA4C-B0E3-4583-BAEF-1F1B2E483E56")
    val iVector: Guid = Guid("913337E9-11A1-4345-A3A2-4E7F956E222D")
    val iMap: Guid = Guid("3C2925FE-8519-45C1-AA79-197B6718C1C1")
    val iMapView: Guid = Guid("E480CE40-A338-4ADA-ADCF-272272E48CB9")

    fun keyValuePair(key: WinRtTypeSignature, value: WinRtTypeSignature): Guid =
        parameterized(iKeyValuePair, key, value)

    fun iterable(element: WinRtTypeSignature): Guid =
        parameterized(iIterable, element)

    fun vectorView(element: WinRtTypeSignature): Guid =
        parameterized(iVectorView, element)

    fun vector(element: WinRtTypeSignature): Guid =
        parameterized(iVector, element)

    fun map(key: WinRtTypeSignature, value: WinRtTypeSignature): Guid =
        parameterized(iMap, key, value)

    fun mapView(key: WinRtTypeSignature, value: WinRtTypeSignature): Guid =
        parameterized(iMapView, key, value)

    fun keyValuePairSignature(key: WinRtTypeSignature, value: WinRtTypeSignature): WinRtTypeSignature =
        WinRtTypeSignature.parameterizedInterface(iKeyValuePair, key, value)

    fun iterableSignature(element: WinRtTypeSignature): WinRtTypeSignature =
        WinRtTypeSignature.parameterizedInterface(iIterable, element)

    fun vectorViewSignature(element: WinRtTypeSignature): WinRtTypeSignature =
        WinRtTypeSignature.parameterizedInterface(iVectorView, element)

    fun vectorSignature(element: WinRtTypeSignature): WinRtTypeSignature =
        WinRtTypeSignature.parameterizedInterface(iVector, element)

    fun mapSignature(key: WinRtTypeSignature, value: WinRtTypeSignature): WinRtTypeSignature =
        WinRtTypeSignature.parameterizedInterface(iMap, key, value)

    fun mapViewSignature(key: WinRtTypeSignature, value: WinRtTypeSignature): WinRtTypeSignature =
        WinRtTypeSignature.parameterizedInterface(iMapView, key, value)

    private fun parameterized(genericInterface: Guid, vararg arguments: WinRtTypeSignature): Guid =
        ParameterizedInterfaceId.createFromSignature(
            WinRtTypeSignature.parameterizedInterface(genericInterface, *arguments),
        )
}
