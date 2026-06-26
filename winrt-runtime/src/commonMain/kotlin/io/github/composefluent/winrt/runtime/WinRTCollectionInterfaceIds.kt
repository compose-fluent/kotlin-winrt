package io.github.composefluent.winrt.runtime

object WinRTCollectionInterfaceIds {
    val iKeyValuePair: Guid = Guid("02B51929-C1C4-4A7E-8940-0312B5C18500")
    val iIterable: Guid = Guid("FAA585EA-6214-4217-AFDA-7F46DE5869B3")
    val iIterator: Guid = Guid("6A79E863-4300-459A-9966-CBB660963EE1")
    val iVectorView: Guid = Guid("BBE1FA4C-B0E3-4583-BAEF-1F1B2E483E56")
    val iVector: Guid = Guid("913337E9-11A1-4345-A3A2-4E7F956E222D")
    val iMap: Guid = Guid("3C2925FE-8519-45C1-AA79-197B6718C1C1")
    val iMapView: Guid = Guid("E480CE40-A338-4ADA-ADCF-272272E48CB9")

    fun keyValuePair(
        key: WinRTTypeSignature,
        value: WinRTTypeSignature,
    ): Guid = parameterized(iKeyValuePair, key, value)

    fun iterable(element: WinRTTypeSignature): Guid = parameterized(iIterable, element)

    fun iterator(element: WinRTTypeSignature): Guid = parameterized(iIterator, element)

    fun vectorView(element: WinRTTypeSignature): Guid = parameterized(iVectorView, element)

    fun vector(element: WinRTTypeSignature): Guid = parameterized(iVector, element)

    fun map(
        key: WinRTTypeSignature,
        value: WinRTTypeSignature,
    ): Guid = parameterized(iMap, key, value)

    fun mapView(
        key: WinRTTypeSignature,
        value: WinRTTypeSignature,
    ): Guid = parameterized(iMapView, key, value)

    fun keyValuePairSignature(
        key: WinRTTypeSignature,
        value: WinRTTypeSignature,
    ): WinRTTypeSignature = WinRTTypeSignature.parameterizedInterface(iKeyValuePair, key, value)

    fun iterableSignature(element: WinRTTypeSignature): WinRTTypeSignature =
        WinRTTypeSignature.parameterizedInterface(iIterable, element)

    fun iteratorSignature(element: WinRTTypeSignature): WinRTTypeSignature =
        WinRTTypeSignature.parameterizedInterface(iIterator, element)

    fun vectorViewSignature(element: WinRTTypeSignature): WinRTTypeSignature =
        WinRTTypeSignature.parameterizedInterface(iVectorView, element)

    fun vectorSignature(element: WinRTTypeSignature): WinRTTypeSignature =
        WinRTTypeSignature.parameterizedInterface(iVector, element)

    fun mapSignature(
        key: WinRTTypeSignature,
        value: WinRTTypeSignature,
    ): WinRTTypeSignature = WinRTTypeSignature.parameterizedInterface(iMap, key, value)

    fun mapViewSignature(
        key: WinRTTypeSignature,
        value: WinRTTypeSignature,
    ): WinRTTypeSignature = WinRTTypeSignature.parameterizedInterface(iMapView, key, value)

    private fun parameterized(
        genericInterface: Guid,
        vararg arguments: WinRTTypeSignature,
    ): Guid = ParameterizedInterfaceId.createFromSignature(
        WinRTTypeSignature.parameterizedInterface(genericInterface, *arguments),
    )
}
