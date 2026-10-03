package io.github.composefluent.winrt.metadata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Versioned view of XamlCompiler's DOM/harvester, before Kotlin semantic analysis. */
@Serializable
data class WinRTXamlDeclarationIndex(
    @SerialName("SchemaVersion") val schemaVersion: Int,
    @SerialName("Pages") val pages: List<WinRTXamlPageDeclaration>,
    @SerialName("Resources") val resources: List<String>,
)

@Serializable
data class WinRTXamlPageDeclaration(
    @SerialName("ClassName") val className: String,
    @SerialName("ResourcePath") val resourcePath: String,
    @SerialName("BaseTypeName") val baseTypeName: String,
    @SerialName("IsApplication") val isApplication: Boolean,
    @SerialName("Features") val features: List<String>,
    @SerialName("Connections") val connections: List<WinRTXamlConnectionDeclaration>,
)

@Serializable
data class WinRTXamlConnectionDeclaration(
    @SerialName("Id") val id: Int,
    @SerialName("TypeName") val typeName: String,
    @SerialName("FieldName") val fieldName: String?,
    @SerialName("Location") val location: WinRTXamlSourceLocation,
    @SerialName("Events") val events: List<WinRTXamlEventDeclaration>,
    @SerialName("ElementName") val elementName: String? = null,
    @SerialName("ScopeId") val scopeId: Int = 0,
    @SerialName("IsScopeRoot") val isScopeRoot: Boolean = false,
    @SerialName("IsTemplateChild") val isTemplateChild: Boolean = false,
    @SerialName("DataTypeName") val dataTypeName: String? = null,
    @SerialName("Phase") val phase: Int = 0,
    @SerialName("CanBeInstantiatedLater") val canBeInstantiatedLater: Boolean = false,
    @SerialName("IsUnloadableRoot") val isUnloadableRoot: Boolean = false,
    @SerialName("Children") val children: List<Int> = emptyList(),
    @SerialName("Bindings") val bindings: List<WinRTXamlBindingDeclaration> = emptyList(),
)

@Serializable
data class WinRTXamlBindingDeclaration(
    @SerialName("Name") val name: String,
    @SerialName("DeclaringTypeName") val declaringTypeName: String,
    @SerialName("TypeName") val typeName: String,
    @SerialName("Mode") val mode: String,
    @SerialName("Expression") val expression: WinRTXamlBindingExpression,
    @SerialName("Location") val location: WinRTXamlSourceLocation,
    @SerialName("IsAttachable") val isAttachable: Boolean = false,
    @SerialName("IsEvent") val isEvent: Boolean = false,
    @SerialName("IsLoad") val isLoad: Boolean = false,
    @SerialName("Phase") val phase: Int = 0,
    @SerialName("BindBack") val bindBack: WinRTXamlBindingExpression? = null,
    @SerialName("Converter") val converter: String? = null,
    @SerialName("ConverterParameter") val converterParameter: String? = null,
    @SerialName("ConverterLanguage") val converterLanguage: String? = null,
    @SerialName("FallbackValue") val fallbackValue: WinRTXamlBindingExpression? = null,
    @SerialName("TargetNullValue") val targetNullValue: WinRTXamlBindingExpression? = null,
    @SerialName("UpdateSourceTrigger") val updateSourceTrigger: String? = null,
)

/** Syntax exported by XamlCompiler's BindingPath parser; it is never interpreted at runtime. */
@Serializable
data class WinRTXamlBindingExpression(
    @SerialName("Kind") val kind: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("TypeName") val typeName: String? = null,
    @SerialName("Value") val value: String? = null,
    @SerialName("Receiver") val receiver: WinRTXamlBindingExpression? = null,
    @SerialName("Arguments") val arguments: List<WinRTXamlBindingExpression> = emptyList(),
)

@Serializable
data class WinRTXamlEventDeclaration(
    @SerialName("Name") val name: String,
    @SerialName("HandlerName") val handlerName: String,
    @SerialName("DeclaringTypeName") val declaringTypeName: String,
    @SerialName("DelegateTypeName") val delegateTypeName: String,
    @SerialName("Location") val location: WinRTXamlSourceLocation,
)

@Serializable
data class WinRTXamlSourceLocation(
    @SerialName("Line") val line: Int,
    @SerialName("Column") val column: Int,
)

object WinRTXamlDeclarations {
    const val SCHEMA_VERSION = 2
    private val json = Json { encodeDefaults = true }
    private val supportedFeatures = setOf("named-elements", "events", "compiled-bindings", "templates", "phased-bindings", "deferred-elements")

    /** Never accept a partial/stale index from a compiler invocation that reported an error. */
    fun readCompilerOutput(path: Path): WinRTXamlDeclarationIndex {
        val output = json.parseToJsonElement(Files.readString(path)).jsonObject
        val errors = output["MSBuildLogEntries"]?.jsonArray.orEmpty().filter {
            it.jsonObject["Type"]?.jsonPrimitive?.intOrNull == 2
        }
        require(errors.isEmpty()) { "XamlCompiler failed: " + errors.joinToString("; ") {
            it.jsonObject["Message"]?.jsonPrimitive?.content.orEmpty()
        } }
        val index = output["KotlinDeclarations"]
        require(index != null && index != JsonNull) { "XamlCompiler did not produce Kotlin declarations: $path" }
        return parse(index.toString())
    }

    fun parse(text: String): WinRTXamlDeclarationIndex {
        val element = json.parseToJsonElement(text)
        require(element.jsonObject["SchemaVersion"]?.jsonPrimitive?.intOrNull in 1..SCHEMA_VERSION) {
            "Unsupported Kotlin XAML declaration schema; expected $SCHEMA_VERSION."
        }
        return json.decodeFromJsonElement<WinRTXamlDeclarationIndex>(element).also(::validate)
    }

    fun canonicalText(index: WinRTXamlDeclarationIndex): String {
        validate(index)
        return json.encodeToString(index.copy(
            resources = index.resources.sorted(),
            pages = index.pages.sortedBy { it.className }.map { page -> page.copy(
                features = page.features.sorted(),
                connections = page.connections.sortedBy { it.id }.map { connection -> connection.copy(
                    events = connection.events.sortedBy { it.name },
                    bindings = connection.bindings.sortedBy { it.name },
                    children = connection.children.sorted(),
                ) },
            ) },
        ))
    }

    fun fingerprint(index: WinRTXamlDeclarationIndex): String = MessageDigest.getInstance("SHA-256")
        .digest(canonicalText(index).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun validate(index: WinRTXamlDeclarationIndex) {
        require(index.schemaVersion in 1..SCHEMA_VERSION) { "Unsupported Kotlin XAML schema ${index.schemaVersion}." }
        require(index.pages.map { it.className }.distinct().size == index.pages.size) { "Duplicate x:Class." }
        val paths = index.resources + index.pages.map { it.resourcePath }
        paths.forEach(::validateResourcePath)
        require(paths.map { it.lowercase(java.util.Locale.ROOT) }.distinct().size == paths.size) { "Duplicate XAML resource path." }
        for (page in index.pages) {
            require(page.className.isNotBlank() && page.baseTypeName.isNotBlank()) { "Missing XAML class or base type." }
            require(page.features.all { it in supportedFeatures }) { "Unsupported Kotlin XAML features: ${page.features - supportedFeatures}" }
            require(page.connections.map { it.id }.distinct().size == page.connections.size) { "Duplicate connection ID in ${page.resourcePath}." }
            val fields = page.connections.filterNot { it.isTemplateChild }.mapNotNull { it.fieldName }
            require(fields.distinct().size == fields.size) { "Duplicate x:Name in ${page.resourcePath}." }
            require(fields.isEmpty() || "named-elements" in page.features) { "Missing named-elements feature." }
            require(page.connections.all { it.events.isEmpty() } || "events" in page.features) { "Missing events feature." }
            require(page.connections.all { it.bindings.isEmpty() } ||
                (index.schemaVersion >= 2 && "compiled-bindings" in page.features)) { "Missing compiled-bindings feature." }
            for (connection in page.connections) {
                require(connection.id > 0 && connection.typeName.isNotBlank()) { "Invalid XAML connection in ${page.resourcePath}." }
                require(connection.fieldName == null || connection.fieldName.isNotBlank()) { "Empty x:Name." }
                require(connection.phase in 0..31) { "Invalid XAML phase." }
                require(connection.phase == 0 || "phased-bindings" in page.features) { "Missing phased-bindings feature." }
                require(!connection.canBeInstantiatedLater || "deferred-elements" in page.features) { "Missing deferred-elements feature." }
                require(connection.children.distinct().size == connection.children.size &&
                    connection.children.all { child -> child != connection.id && page.connections.any { it.id == child } }) { "Invalid deferred child connection." }
                validateLocation(connection.location)
                require(connection.events.map { it.name }.distinct().size == connection.events.size) { "Duplicate XAML event." }
                for (event in connection.events) {
                    require(listOf(event.name, event.handlerName, event.declaringTypeName, event.delegateTypeName).all(String::isNotBlank)) { "Incomplete XAML event in ${page.resourcePath}." }
                    validateLocation(event.location)
                }
                require(connection.bindings.map { it.name }.distinct().size == connection.bindings.size) { "Duplicate compiled binding." }
                for (binding in connection.bindings) {
                    require(listOf(binding.name, binding.declaringTypeName, binding.typeName).all(String::isNotBlank) &&
                        binding.mode in setOf("OneTime", "OneWay", "TwoWay")) { "Incomplete compiled binding in ${page.resourcePath}." }
                    require(connection.scopeId > 0) { "Compiled binding has no scope in ${page.resourcePath}." }
                    require(binding.phase in 0..31) { "Invalid compiled binding phase." }
                    require(!binding.isLoad || connection.isUnloadableRoot) { "x:Load binding has no unloadable element." }
                    validateExpression(binding.expression)
                    listOfNotNull(binding.bindBack, binding.fallbackValue, binding.targetNullValue).forEach(::validateExpression)
                    validateLocation(binding.location)
                }
            }
        }
    }

    private fun validateExpression(expression: WinRTXamlBindingExpression) {
        require(expression.kind in setOf("root", "member", "call", "static", "cast", "index", "attached", "literal")) {
            "Unsupported compiled binding expression ${expression.kind}."
        }
        if (expression.kind in setOf("member", "call", "attached")) require(!expression.name.isNullOrBlank()) { "Binding member has no name." }
        if (expression.kind in setOf("static", "cast", "attached", "literal")) require(!expression.typeName.isNullOrBlank()) { "Binding expression has no type." }
        if (expression.kind in setOf("member", "call", "cast", "index", "attached")) require(expression.receiver != null) { "Binding expression has no receiver." }
        expression.receiver?.let(::validateExpression)
        expression.arguments.forEach(::validateExpression)
    }

    private fun validateLocation(location: WinRTXamlSourceLocation) {
        require(location.line > 0 && location.column > 0) { "Missing XAML source location." }
    }

    private fun validateResourcePath(path: String) {
        require(path.isNotBlank() && !path.startsWith('/') && ':' !in path && '\\' !in path &&
            path.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "Invalid relative XAML resource path: $path" }
    }
}
