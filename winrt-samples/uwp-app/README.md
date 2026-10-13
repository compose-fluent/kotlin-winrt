# UWP XAML 示例

本模块使用 Windows 自带的 `Windows.UI.Xaml`，同时声明 Kotlin/JVM 和
Kotlin/Native `mingwX64` 目标。页面包含应用资源、`x:Bind`、具名控件和按钮事件，
用于检查投影、XAML 加载及托管对象回调的完整链路。

## 应用模型与源代码

`build.gradle.kts` 的配置选择 UWP 应用模型：

```kotlin
windows {
    application {
        applicationModel.set(WindowsApplicationModel.Uwp)
        windowsAppSdkDeployment.set(WindowsAppSdkDeployment.None)
    }
}
```

这会令 `windows.xaml.framework` 默认采用 `XamlFramework.Uwp`。UWP 应用需要
`WindowsPackageType.Packaged`，使用系统 XAML，不部署 Windows App SDK。
`XamlFramework.WinUi` 选择 Windows App SDK 的 WinUI 3，不能用于此应用。
WinUI 2 的控件虽然也位于 `Microsoft.UI.Xaml` 命名空间，其基础框架仍是 UWP；
使用 WinUI 2 NuGet 控件库时，应继续选择 `XamlFramework.Uwp`。

共享 Kotlin、XAML 和 Appx 资源仍放在 `src/winuiMain`。`winuiMain` 是兼容既有工程
布局的 source set 名称；实际框架由上述配置决定。JVM 和 Native 各自的实现分别在
`src/jvmMain` 和 `src/mingwX64Main`。

被 UWP 应用使用的 XAML 库没有应用模型可供推断，必须自行选择相同框架：

```kotlin
import io.github.composefluent.winrt.runtime.XamlFramework

windows {
    xaml {
        framework.set(XamlFramework.Uwp)
    }
}
```

生成的应用入口会在调用用户 `main` 前初始化 UWP 运行时。应用通过
`Application.start` 启动，在 `onLaunched` 中设置 `Window.current.content` 并激活窗口。
UWP 的 `LaunchActivatedEventArgs` 来自 `Windows.ApplicationModel.Activation`。

## 构建环境与 XAML 编译器

请在 Windows x64 上使用 PowerShell，并准备 JDK 25、Windows SDK 10.0.26100.0、
Windows C/C++ 构建工具和编译器包声明所需的 .NET Framework。示例的 SDK 版本固定在
`packageReferences.windowsSdk("10.0.26100.0", includeExtensions = false)`；这与
应用声明的最低系统版本 `10.0.19041.0` 是两个独立配置。

UWP 必须使用配套 `kotlin-winrt-xamlc` 仓库 `codex/uwp-support` 分支构建的 UWP
编译器包。该分支已有本地构建包；本模块通过 `compilerDirectory` 指向解包后的目录。
插件默认下载的 WinUI 编译器包不能用于 UWP。

编译器目录必须包含 `kotlin-xamlc.json`、`XamlCompiler.exe` 和清单列出的依赖文件。
清单需声明 `compatibility.frameworks` 包含 `uwp`、`genXbfHost` 为 `win-x64`，并在
`windowsSdkVersions` 中列出所选 SDK。插件会校验协议、文件校验和及这些兼容条件。

UWP 使用同版本 Windows SDK 提供的 `GenXbf.dll`，通常位于
`<Windows Kits 根目录>\bin\10.0.26100.0\XamlCompiler\x64\GenXbf.dll`。
插件会尝试自动定位；手工设置 `genXbfDirectory` 时应指向包含 `x64` 子目录的
`XamlCompiler` 目录。不要替换成 WinUI NuGet 包内的 GenXbf。

## 构建和运行

以下命令均在仓库根目录执行。先将占位路径替换为本机实际路径：

```powershell
$xamlCompiler = 'C:\path\to\unpacked-uwp-xaml-compiler'
$genXbf = 'C:\path\to\Windows Kits\10\bin\10.0.26100.0\XamlCompiler'
$xamlOptions = @(
    "-PkotlinWinRT.xaml.compilerDirectory=$xamlCompiler"
    "-PkotlinWinRT.xaml.genXbfDirectory=$genXbf"
)

# 生成 JVM 开发包布局。
.\gradlew.bat ':winrt-samples:uwp-app:stageWinAppDevelopmentPackageJvmMain' @xamlOptions

# 注册开发包并通过 UWP 激活启动 JVM 版本。
.\gradlew.bat ':winrt-samples:uwp-app:runWinAppPackageJvmMain' @xamlOptions --detach

# 注册并启动 Kotlin/Native Debug 版本。
.\gradlew.bat ':winrt-samples:uwp-app:runWinAppPackageMingwX64MainDebugExecutable' @xamlOptions --detach

# 注册并启动 Kotlin/Native Release 版本。
.\gradlew.bat ':winrt-samples:uwp-app:runWinAppPackageMingwX64MainReleaseExecutable' @xamlOptions --detach
```

运行任务会构建依赖、暂存开发包，使用 `Add-AppxPackage -Register` 注册并通过
Windows 应用激活接口启动。开发机需允许开发包注册。`--detach` 在激活后返回；省略它
则等待应用退出。JVM 和 Native 变体共享该示例的包身份，应逐个运行。
直接执行 EXE、普通 JVM `run` 或 Native `runDebugExecutableMingwX64` 不提供此处需要的
UWP 包激活环境。

JVM 版本默认使用 `WinAppJvmRuntimeMode.Bundled`：插件以 JDK 25 的 `jlink` 生成
运行时镜像，将其放入包的 `runtime` 目录，并把应用依赖放入 `lib`。也可以通过
`windows.application.jvmRuntimeImage` 指定满足目标架构和 Java 版本要求的预构建镜像。
UWP 不接受 `External` JVM 模式，部署后的应用不依赖机器上的 `JAVA_HOME`。
Native 版本由 `mingwX64` 生成可执行文件，不携带 JVM。

## Manifest 与检查范围

源 Manifest 在 `src/winuiMain/appxResources/AppxManifest.xml`，`EntryPoint` 保留应用
类名 `io.github.composefluent.winrt.samples.uwp.UwpApplication`。暂存阶段会设置
`uap10:RuntimeBehavior="windowsApp"` 与 `uap10:TrustLevel="appContainer"`。

Manifest 声明 `codeGeneration`，供运行时的动态 ABI 回调代码使用。回调分配使用
FromApp API，写入 RW 内存后转为 RX；AppContainer 中转为可执行页需要该能力。
JVM 自身的解释器、JIT 和 FFM 也涉及动态代码，因此必须通过真实打包启动单独验证。
普通 desktop 单元测试不能替代这项检查。

启动后应检查标题绑定、应用级画刷资源、按钮点击和计数文本更新。示例将启动、页面加载
和点击记录写入 `ApplicationData.current.localFolder` 下的 `uwp-sample.log`。
此目录属于当前开发包的应用数据目录。UWP 运行任务目前不提供 WinUI 设计器、热重载或
`--debug-output`；启动输出会报告进程 ID，可据此附加调试器。

JVM 实机启动目前受 OpenJDK 25.0.4 的 AppContainer 文件访问兼容性阻塞：
JDK 加载包内 `conf/security/java.security` 时，`WindowsPath.toRealPath()`
逐级解析父目录并抛出 `AccessDeniedException`。文件已经打包；仅构建成功和包激活
成功不能证明 JVM UWP 可用。需要兼容 AppContainer 的 JDK 文件系统实现后继续验证。

Native Debug 和 Release 均已通过实际包激活，进程令牌确认 `TokenIsAppContainer=1`，并完成
应用资源画刷、标题 `x:Bind`、具名控件、按钮回调及计数文本更新的实机验证。
