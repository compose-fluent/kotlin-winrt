package io.github.kitectlab.winrt.runtime

internal fun GuidGenerator.getGuid(
    type: Class<*>,
): Guid = getGuid(type.registeredKClass())

internal fun GuidGenerator.getIID(
    type: Class<*>,
): Guid = getIID(type.registeredKClass())

internal fun GuidGenerator.getSignature(
    type: Class<*>,
): String = getSignature(type.registeredKClass())

internal fun GuidGenerator.createIID(
    type: Class<*>,
): Guid = createIID(type.registeredKClass())
