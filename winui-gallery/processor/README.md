# Gallery code highlighting

Code previews use IDEA 2026.2's default foreground styles. The reference is the
JetBrains `intellij-community` tag `idea/262.8665.337`:

- `platform/platform-resources/src/themes/expUI/expUI_lightScheme.xml`
- `platform/platform-resources/src/themes/islands/IslandSchemeDark.xml`
- `plugins/kotlin/highlighting/highlighting-minimal/src/org/jetbrains/kotlin/idea/highlighter/KotlinHighlightingColors.java`
- `plugins/kotlin/highlighting/highlighting-k2/src/org/jetbrains/kotlin/idea/highlighting/analyzers/`

KSP extracts preview text with a UTF-16 source map and emits syntax-colored spans.
Each identifier keeps its own span and its location in the original source file.
`GalleryHighlightingCompilerPlugin` runs in the application's actual K2 compilation,
collects resolved FIR symbols, and replaces those spans' enum constants in IR.
The resulting application contains only text and final spans; no compiler or
semantic index is shipped with it.

Migrated XAML pages keep independent `SampleDefinitions/<route>/*.txt` files in
the page's directory, following upstream `ControlExample.SampleDefinition`.
Each definition has `header`, `xaml`, and optional `kotlin` sections. The catalog
selects both languages by example title; it never repeats the complete page for
each example. A markup-only example has only an XAML tab. Both markup and sample
definition files participate in the KSP input fingerprint.

These standalone display fragments use syntax highlighting, since they are not
compilation units with a source origin. Source-extracted Kotlin examples retain
the FIR-based semantic coloring described below. The definition text is display
data only; the adjacent page files remain the compiled implementation.

Resolution uses the actual target's source files and dependencies, including WinUI
projections. It distinguishes declarations, constructor/member/package/extension
calls, local and captured variables, parameters, instance/package/extension
properties, enum entries, and type parameters. Named arguments, comments, string
escapes, and annotations retain syntax highlighting. Synthetic FIR nodes are
excluded except the source-backed reference wrappers used for property access and
implicit invocation. Unresolved names retain the neutral syntax fallback; the
normal compiler still diagnoses invalid application source.

The plugin is attached to both JVM and mingwX64 application compilation, not to
projection compilation or unrelated modules. Gallery JVM incremental compilation
is disabled so original files and generated previews always share a FIR session;
Gradle's up-to-date checks and build cache still apply. The origin annotation has
source retention and carries no runtime dependency. This is Gallery presentation
tooling, separate from CsWinRT-aligned projection/runtime semantics.

The scope is default text foreground and italic styles, not an IDE implementation:
it does not add diagnostics, rainbow identifiers, smart-cast backgrounds, mutable
variable underlines, user theme overrides, or live editing. It preserves the
Gallery's font family, surface background, and high-contrast override.

Validation: `:winui-gallery:processor:test` compiles a real Kotlin fixture through
the plugin and inspects the generated document. Application validation uses
`:winui-gallery:compileKotlinWinuiJvm` and `:winui-gallery:compileKotlinMingwX64`.

Native application validation is currently blocked by the generated application
entry point calling `userMain()` while Gallery requires an `args` parameter.
The plugin is wired for Native, but end-to-end Native verification remains open
until the application-entry generator contract is repaired in its owning module.
