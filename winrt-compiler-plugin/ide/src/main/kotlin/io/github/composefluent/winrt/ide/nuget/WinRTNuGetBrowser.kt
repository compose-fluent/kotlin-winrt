package io.github.composefluent.winrt.ide.nuget

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.util.io.HttpRequests
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class WinRTNuGetSource(val name: String, val address: String, val config: Path?, val requiresProvider: Boolean = false,
    private val authorization: String? = null) {
    internal fun authorize(endpoint: URI): String? {
        val origin = URI(address)
        return authorization?.takeIf { origin.scheme == "https" && endpoint.scheme == origin.scheme &&
            endpoint.host.equals(origin.host, true) && endpoint.port == origin.port }
    }
    override fun toString() = name
}

object WinRTNuGetSources {
    /** Browsing is read-only; restore continues to use WinApp's complete NuGet configuration/providers. */
    fun read(base: Path, userDirectory: Path? = System.getenv("APPDATA")?.let { Path.of(it).resolve("NuGet") },
        machineDirectory: Path? = System.getenv("ProgramFiles(x86)")?.let { Path.of(it).resolve("NuGet/Config") }): List<WinRTNuGetSource> {
        val files = mutableListOf<Path>()
        fun addDirectory(dir: Path?) {
            if (dir != null && Files.isDirectory(dir)) Files.list(dir).use { paths ->
                files += paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".config", true) }.sorted().toList()
            }
        }
        addDirectory(machineDirectory)
        addDirectory(userDirectory?.resolve("config"))
        userDirectory?.resolve("NuGet.Config")?.takeIf(Files::isRegularFile)?.let { files.add(it) }
        generateSequence(base.toAbsolutePath().normalize()) { it.parent }.toList().asReversed().forEach { dir ->
            if (Files.isDirectory(dir)) Files.list(dir).use { paths ->
                files += paths.filter { Files.isRegularFile(it) && it.fileName.toString().equals("NuGet.Config", true) }.sorted().toList()
            }
        }
        val sources = linkedMapOf<String, WinRTNuGetSource>()
        val disabled = linkedMapOf<String, String>()
        val credentials = linkedMapOf<String, Map<String, String>>()
        var hasSources = false
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        files.distinct().forEach { file ->
            val root = factory.newDocumentBuilder().parse(file.toFile()).documentElement
            root.children().forEach { section ->
                when {
                    section.tagName.equals("packageSources", true) -> {
                        hasSources = true
                        section.children().forEach { item ->
                            val key = item.getAttribute("key")
                            when (item.tagName.lowercase()) {
                                "clear" -> sources.clear()
                                "remove" -> sources.remove(key.lowercase())
                                "add" -> {
                                    val address = expand(item.getAttribute("value"))
                                    val uri = runCatching { URI(address) }.getOrNull()
                                    require(uri?.userInfo == null) { "Source '$key' contains credentials in its URL. Use NuGet source credentials." }
                                    val value = if (uri?.scheme in listOf("https", "http")) address else file.parent.resolve(address).normalize().toString()
                                    sources[key.lowercase()] = WinRTNuGetSource(key, value, file)
                                }
                            }
                        }
                    }
                    section.tagName.equals("disabledPackageSources", true) -> section.children().forEach {
                        when (it.tagName.lowercase()) {
                            "clear" -> disabled.clear()
                            "remove" -> disabled.remove(it.getAttribute("key").lowercase())
                            "add" -> disabled[it.getAttribute("key").lowercase()] = it.getAttribute("value")
                        }
                    }
                    section.tagName.equals("packageSourceCredentials", true) -> section.children().forEach { source ->
                        credentials[source.tagName.replace("_x0020_", " ").lowercase()] = source.children()
                            .filter { it.tagName.equals("add", true) }.associate { it.getAttribute("key").lowercase() to expand(it.getAttribute("value")) }
                    }
                }
            }
        }
        if (!hasSources) sources["nuget.org"] = WinRTNuGetSource("nuget.org", "https://api.nuget.org/v3/index.json", null)
        return sources.values.filterNot { disabled[it.name.lowercase()].equals("true", true) }.map { source ->
            val env = System.getenv("NuGetPackageSourceCredentials_${source.name}")?.split(';')?.mapNotNull {
                val split = it.indexOf('='); if (split < 0) null else it.substring(0, split).lowercase() to it.substring(split + 1)
            }?.toMap()
            val config = credentials[source.name.lowercase()]
            val user = env?.get("username") ?: config?.get("username")
            val password = env?.get("password") ?: config?.get("cleartextpassword")
            val authorization = if (user != null && password != null) "Basic " + Base64.getEncoder()
                .encodeToString("$user:$password".toByteArray(Charsets.UTF_8)) else null
            WinRTNuGetSource(source.name, source.address, source.config, config?.containsKey("password") == true && authorization == null, authorization)
        }
    }

    private fun expand(value: String) = Regex("%([^%]+)%").replace(value) { System.getenv(it.groupValues[1]) ?: it.value }
    private fun Element.children(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
}

data class WinRTNuGetSearchResult(val id: String, val version: String, val description: String, val authors: String, val versions: List<String>,
    val downloads: Long = 0, val projectUrl: String = "")
data class WinRTNuGetSearchPage(val packages: List<WinRTNuGetSearchResult>, val total: Int)
data class WinRTNuGetDependencyGroup(val framework: String, val dependencies: List<Pair<String, String>>)
data class WinRTNuGetPackageDetails(val description: String, val authors: String, val projectUrl: String, val license: String,
    val published: String, val dependencies: List<WinRTNuGetDependencyGroup>, val deprecation: String = "")

class WinRTNuGetBrowser(private val source: WinRTNuGetSource, private val load: (URI) -> JsonObject = { endpoint ->
    require(endpoint.scheme in listOf("https", "http")) { "This source requires the NuGet CLI. Enter an exact package ID/version to add it." }
    require(endpoint.userInfo == null) { "Credentials in source URLs are not supported." }
    HttpRequests.request(endpoint.toString()).connectTimeout(15_000).readTimeout(15_000).followRedirects(false)
        .accept("application/json").tuner { connection -> source.authorize(endpoint)?.let { connection.setRequestProperty("Authorization", it) } }
        .connect { request ->
            val bytes = request.inputStream.use { it.readNBytes(8 * 1024 * 1024 + 1) }
            require(bytes.size <= 8 * 1024 * 1024) { "The NuGet response exceeds 8 MiB." }
            JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        }
}) {
    private val index by lazy { load(URI(source.address)) }
    private fun resource(name: String): URI = index["resources"].asJsonArray.filter { value ->
        val type = value.asJsonObject["@type"]
        (if (type.isJsonArray) type.asJsonArray.map { it.asString } else listOf(type.asString)).any { it.substringBefore('/') == name }
    }.maxByOrNull { it.asJsonObject["@type"].toString().contains("3.6.0") }?.asJsonObject?.get("@id")?.asString?.let { URI(source.address).resolve(it) }
        ?: error("Source '${source.name}' does not expose the NuGet V3 $name service.")

    fun search(query: String, prerelease: Boolean, skip: Int = 0): List<WinRTNuGetSearchResult> = searchPage(query, prerelease, skip).packages

    fun searchPage(query: String, prerelease: Boolean, skip: Int = 0): WinRTNuGetSearchPage {
        require(skip >= 0)
        val endpoint = resource("SearchQueryService").toString()
        val separator = if ('?' in endpoint) '&' else '?'
        val result = load(URI("$endpoint${separator}q=${URLEncoder.encode(query, Charsets.UTF_8)}&prerelease=$prerelease&semVerLevel=2.0.0&skip=$skip&take=40"))
        val packages = result["data"].asJsonArray.map { value ->
            val item = value.asJsonObject
            val versions = item["versions"]?.takeUnless { it.isJsonNull }?.asJsonArray?.map { it.asJsonObject["version"].asString }.orEmpty()
            val authors = item["authors"]?.takeUnless { it.isJsonNull }?.let { if (it.isJsonArray) it.asJsonArray.joinToString { it.asString } else it.asString }.orEmpty()
            WinRTNuGetSearchResult(item["id"].asString, item["version"].asString, item.text("description"), authors, versions,
                item["totalDownloads"]?.takeUnless { it.isJsonNull }?.asLong ?: 0, item.text("projectUrl"))
        }
        return WinRTNuGetSearchPage(packages, result["totalHits"]?.asInt ?: skip + packages.size)
    }

    fun versions(id: String, prerelease: Boolean): List<String> {
        require(Regex("[A-Za-z0-9_.-]+").matches(id)) { "Enter a valid package ID." }
        val root = resource("PackageBaseAddress").toString().trimEnd('/')
        return load(URI("$root/${id.lowercase()}/index.json"))["versions"].asJsonArray.map { it.asString }
            .filter { prerelease || '-' !in it }.sortedWith { a, b -> WinRTNuGetVersion.compare(b, a) }
    }

    /** Registration leaf -> catalog entry, including feeds that publish the entry as a URL. */
    fun details(id: String, version: String): WinRTNuGetPackageDetails {
        require(Regex("[A-Za-z0-9_.-]+").matches(id)) { "Enter a valid package ID." }
        val normalized = WinRTNuGetVersion.normalized(version)
        val root = resource("RegistrationsBaseUrl").toString().trimEnd('/')
        val leaf = load(URI("$root/${id.lowercase()}/${normalized.lowercase()}.json"))
        val entry = leaf["catalogEntry"]?.let { if (it.isJsonObject) it.asJsonObject else load(URI(it.asString)) }
            ?: error("The package source did not return version metadata.")
        val groups = entry["dependencyGroups"]?.takeUnless { it.isJsonNull }?.asJsonArray?.toList().orEmpty().map { group ->
            val value = group.asJsonObject
            WinRTNuGetDependencyGroup(value.text("targetFramework"), value["dependencies"]?.takeUnless { it.isJsonNull }?.asJsonArray?.toList().orEmpty().map {
                it.asJsonObject.text("id") to it.asJsonObject.text("range")
            })
        }
        return WinRTNuGetPackageDetails(entry.text("description"), entry.text("authors"), entry.text("projectUrl"),
            entry.text("licenseExpression").ifEmpty { entry.text("licenseUrl") }, entry.text("published"), groups,
            entry["deprecation"]?.takeUnless { it.isJsonNull }?.asJsonObject?.text("message").orEmpty())
    }

    private fun JsonObject.text(name: String): String = get(name)?.takeUnless { it.isJsonNull }?.let {
        if (it.isJsonArray) it.asJsonArray.joinToString { part -> part.asString } else it.asString
    }.orEmpty()
}
