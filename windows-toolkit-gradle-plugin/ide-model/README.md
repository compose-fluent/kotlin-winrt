# IDE project model

`WinRTIdeModel` is the Java 17 Tooling API boundary between the Windows toolkit
Gradle plugin and the IntelliJ integration under `winrt-compiler-plugin/ide`.
It is deliberately independent of Gradle, the IDE SDK, compiler classes and the
Java 25 WinRT runtime. The IDE consumes ordinary configuration facts rather than
loading the projection runtime or generator in its process.

The toolkit registers the model builder when its plugin is applied. Request the
model by its fully qualified interface name. Projects without a `windows`
extension return `enabled = false`. Consumers must check the schema version.

Source sets retain Kotlin roots, direct `dependsOn` edges and the ordered AppX
resource roots produced by the existing toolkit resource layout. Targets retain
their platform and source-set membership, including JVM and Native targets.
NuGet entries are declared references, not resolved package graphs. Importing
the model does not resolve dependency configurations or execute projection,
restore, native compilation or packaging tasks. Credentials and signing options
are not exported.

WinMD ingestion and projection rules continue to belong to `winrt-metadata` and
`winrt-generator`, corresponding to `.cswinrt/src/cswinrt`. This model adds an IDE
transport boundary, not a second projection model. Resolved metadata, XAML
compiler inputs, resource provenance and named application manifests require
further contract additions before their IDE features can be supported.

Validate on Windows with:

```powershell
./gradlew.bat -p windows-toolkit-gradle-plugin :test --tests '*WinRTIdeModelBuilderTest'
```
