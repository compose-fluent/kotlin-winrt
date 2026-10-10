# Releasing Kotlin/WinRT

The [Release workflow](../.github/workflows/release.yml) runs when a stable `vX.Y.Z` tag is pushed. It checks the tag, `winrt.baseVersion` and `CHANGELOG.md`, builds signed JVM/Native Gallery packages and all three IDE variants, validates Maven publications locally and automatically publishes to Maven Central. After Central succeeds, separate jobs submit the Gradle plugin to Gradle Plugin Portal and create one GitHub Release with the changelog entry and checked assets.

## Credentials

Make these repository or organization secrets available to this repository's Actions runs:

- `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`: Central Portal user-token credentials for the verified `io.github.compose-fluent` namespace.
- `MAVEN_GPG_KEY_ID`, `MAVEN_GPG_KEY_ARMOR`, `MAVEN_GPG_KEY_PASSWORD`: ASCII-armored signing key and credentials. Its public key must be discoverable by Central.
- `WINUI_GALLERY_SIGNING_PFX_BASE64`, `WINUI_GALLERY_SIGNING_PFX_PASSWORD`: unexpired signing PFX whose subject exactly matches `CN=ComposeFluent` in the Gallery manifest.
- `GRADLE_PUBLISH_KEY`, `GRADLE_PUBLISH_SECRET`: Gradle Plugin Portal API credentials, used by the `gradle-portal` job after Maven Central publication.

Environment-scoped credentials require the corresponding workflow jobs to select that environment before tagging. Organization secrets must grant this repository access. The workflow checks availability without logging values. Only the public certificate is attached to the release; private signing material is removed from the runner after packaging.

The test branch successfully submitted `0.0.9-build1`, but the plugin is still awaiting [Gradle Plugin Portal's manual review](https://plugins.gradle.org/docs/publish-plugin). A successful submission does not confirm public availability. Keep the README's explicit `pluginManagement.resolutionStrategy` configuration until approval; it resolves the implementation from Maven Central.

## Prepare and tag

1. Review the preparation branch, base version, changelog, README examples and compatibility notes.
2. Validate on Windows with JDK 25, MSVC and the configured SDK baselines. The workflow checks both runtime targets, both supported compiler versions, IDE tests and local publication generation. Gallery packaging builds and signs both application targets.
3. Merge the reviewed preparation into `master`, then tag the intended commit:

```powershell
git switch master
git pull --ff-only origin master
git tag -a v0.1.0 -m "Kotlin/WinRT 0.1.0"
git push origin v0.1.0
```

A local tag alone does not trigger GitHub Actions. Do not move a tag after publication.

To validate locally without uploading to Central:

```powershell
./.github/scripts/publish-release.ps1 -Tag v0.1.0 -Mode Verify
```

`-DryRun` inspects Gradle task graphs without producing or uploading publications. `Verify` writes to Maven Local; use a disposable environment if isolation is required.

## Outputs

Core runtime, metadata, generator, authoring, compiler/callsite modules (including Kotlin 2.4.20 variants) and the toolkit use `0.1.0`. Prebuilt projections retain metadata-baseline versions: Windows SDK and Windows.UI.Xaml for the four SDKs in `gradle.properties`, plus WebView2 and Windows App SDK 2.5.1. WebView2 is included because the App SDK projection depends on it. IDE project-release packages bundle and select `0.1.0`; standalone `ide-v*` releases retain their independent snapshot-toolchain behavior.

The `gradle-portal` job submits `io.github.compose-fluent.windows-toolkit:0.1.0` for tag `v0.1.0`. It checks the implementation JAR and marker versions, runs `publishPlugins --validate-only`, then runs `publishPlugins` with the same tag version. Its JARs, POMs and validation reports are retained as an Actions artifact.

GitHub assets are the two `Kotlin-WinUI-Gallery-0.1.0-{jvm,mingwX64}.msix` packages, three `kotlin-winrt-ide-0.1.0-{idea-262,as-261,as-canary-262}.zip` packages, `winui-gallery-signing.cer` and `SHA256SUMS.txt`.

The Gallery manifest uses `0.1.0.0` and the production identity; snapshots retain their `.snapshot` identity. Release targets share one identity and are alternative installations. Snapshot and standalone IDE workflows remain available separately.

## Failure recovery

Maven Central and GitHub Releases are separate services, not an atomic transaction. All application builds and local Maven checks finish before the first Central upload. The pipeline uses [`publishAndReleaseToMavenCentral`](https://vanniktech.github.io/gradle-maven-publish-plugin/central/#uploading-with-automatic-publishing) to automatically release uploads rather than leave them waiting for a manual Portal action. Central indexing can lag behind publication.

Gradle Plugin Portal submission runs independently of GitHub Release creation. A Portal failure marks its job as failed without blocking the GitHub Release or undoing Maven Central publication. Fix that job and rerun it separately; check whether the version was already submitted before retrying. Manual review is handled by Gradle outside CI.

For failures before upload, fix the cause and rerun failed jobs for the same tag. After partial Central publication, inspect deployment status and already-published coordinates before retrying: released coordinates cannot be overwritten. Do not blindly rerun every publication, particularly metadata-versioned projections. Complete only missing publications through a reviewed recovery change. If only GitHub Release creation fails, rerun that job with existing artifacts; do not republish Maven artifacts. If a release already exists, inspect its assets instead of creating another release.
