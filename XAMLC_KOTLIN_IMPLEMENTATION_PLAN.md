# XamlCompiler Kotlin 支持：本地实现与 Luna 交接计划

## 目标与执行边界

为现有 Microsoft XamlCompiler 增加 Kotlin 支持，接入 kotlin-winrt 的编译器插件、authoring、运行时与 Windows 构建流程。默认采用同目录 XAML 自动发现，不要求 `@XamlPage` 等类注解，不要求维护 Kotlin 编译器 fork。

本文件是本次任务的独立实现交接计划。根 `PLAN.md` 仍是仓库规范计划；本文件不替换其中已有的性能任务，不授权执行者重写其条目或结构。先完成编译链路的最小闭环，最终必须把现有 `winui-gallery` 从纯 Kotlin 构建 UI 迁移为 XAML + Kotlin，并在 Gallery 内完成验收。单个示例成功只是中间里程碑。

- Kotlin 仓库：`E:\Documents\AndroidStudioProjects\kotlin-winrt`
- XamlCompiler 仓库：`I:\Visual Studio Project\kotlin-winrt-xamlc`
- Kotlin 工作分支：`xaml-support`。
- XamlCompiler 工作分支：使用已有的 `kotlin-xamlc`，不要在该仓库另建 `xaml-support` 作为实现或 CI 发布来源。两仓库分支名不必一致；CI 的分支过滤、手动发布入口和文档示例均应采用各自的实际分支。
- XamlCompiler 范围：`src/XamlCompiler`
- 定制 XamlCompiler 发布仓库：`https://github.com/compose-fluent/microsoft-ui-xaml`（本地 fork 的 origin 已核实；用户口述 `microst-xaml-ui` 对应此仓库，若后续明确改名，以实际配置为准）。由该仓库 CI 构建发布，Kotlin/WinRT Gradle 插件解析并调用发布产物。
- 运行时及 authoring 的首要参考：Kotlin 仓库 `.cswinrt/src`。
- XAML 编译语义的首要参考：XamlCompiler 现有 C# 与 C++/WinRT 后端。
- 实现先建立 JVM 闭环；共享描述与生成代码必须保留 `mingwX64` 可实现性，Native 验收单独记录，不能以 JVM 通过代替。
- 不启动另一聊天或向其他聊天发送消息；此文件由用户稍后交给 Luna 执行。

## GPT6-Luna 执行协议：先读本节

执行模型是 GPT6-Luna。本计划通过限定输入、输出、默认决策及停止条件降低执行歧义，不假设模型能够自行补全尚未验证的架构。下面的执行协议优先于后文较宽泛的工作清单。

### 必须保留的区分

- **用户明确要求**：本地实现计划；无 `@XamlPage` 注解；同目录同主名 XAML 自动识别；使用现有插件实现增强；最终通过将现有 WinUI Gallery 改造成 XAML + Kotlin 验证支持；定制 XamlCompiler 在维护的 fork 仓库由 CI 发布，再由 Kotlin/WinRT 插件引入用于 XAML 编译。
- **本文推荐的默认设计**：`x:Class` 确认身份、FIR+IR、结构化编译协议、分阶段构建。执行者按默认设计落实，不再自行比较若干架构并任选一个。
- **源码事实**：只表示已经读到对应实现，不表示已经通过测试。
- **待验证判断**：FIR 与现有 authoring 的顺序、应用 WinMD 的 loader 兼容性、IDE 加载方式。必须先做下面的验证关口，不能把判断写成完成事实。

### 每次只完成一张工作卡

1. 从表格选取最早未完成且前置条件满足的卡，读取其指定入口及必要调用方。
2. 先说明该卡采用的参考实现、所属模块和具体契约，再编辑；不要通过不断改测试猜设计。
3. 只做该卡所需的最小改动，完成指定验收后再提交。若一个卡发现缺失上游契约，先处理该契约，不能在下游补丁绕过。
4. 错误按“构建配置 / 插件 API / 元数据 / ABI / 生命周期”归类。相同根因经过两次修正仍未解决时，停止扩大修改范围，整理错误和最小复现交回用户决定；不通过降低验收条件继续推进。
5. 每卡结束在单独的 `XAMLC_KOTLIN_EXECUTION_STATUS.md` 保留简短交接：当前卡、已验证事实、两仓库提交、实际命令与退出结果、下一卡和阻塞点。该文件是本地执行记录，不加入提交，也不把日志写进根 `PLAN.md` 或本设计文档。

| 工作卡 | 前置条件 | 明确输入和输出 | 完成判据 / 停止条件 |
| --- | --- | --- | --- |
| G0 环境与源码确认 | 无 | 输入：本文件、两仓库 AGENTS、当前插件配置；输出：实际 Kotlin 版本、compiler 构建入口、相关未提交改动清单 | 能定位已有实现；缺工具或源码先报告具体缺项，不开始大规模编码 |
| G1 插件能力探针 | G0 | 在隔离的工具验证 fixture 中，用外部类名索引为普通类生成一个属性、一个方法和一个接口；IR 中生成同类调用 private 方法的桥接 | 标准 Kotlin 编译器能编译调用方并运行结果；不加注解、不用生成基类、不用 JVM 字节码后处理。失败则仅报告 API/版本障碍，不 fork 编译器 |
| R1 runtime 契约 | G1 | 输入：既有 CCW/composition 和阶段 1；输出：可供生成器调用的连接器、加载与事件生命周期契约 | 接口身份与所有权有针对性验证；不创建独立页面替身，不扩展样例 |
| M1 结构索引 | R1 | 输入：单个 Page XAML、SDK 元数据；输出：确定性声明索引及源位置 | 同一输入字节稳定；有 `x:Class`、根类型、一个命名 Button、普通 Click 事件；结构未知时明确拒绝 |
| M2 用户类型输入 | M1 | 输入：声明索引与 Kotlin 语义符号；输出：应用类型 metadata / 必要符号附表 | XamlCompiler 实际解析页面与事件签名；若要重写整个 reflection/schema 系统才能继续，停在这里交回设计 |
| C1 最小 Kotlin 后端 | M2 | 输入：通过验证的页面模型；输出：声明索引、connection 实现计划和 XBF，必要辅助源码 | connection ID 与重写 XAML 一致；C# 和 C++ 代表性生成回归不变 |
| C2 真实页面 FIR/IR | C1 | 把 G1 能力用于真实投影类，按索引生成属性/接口/方法并消费最终计划 | 页面引用命名控件通过类型检查；private 事件桥接使用正确参数类型；冲突可诊断 |
| A1 authoring 接入 | C2 | 输入：生成接口/方法；输出：页面 CCW 中对应接口条目与分发 | 对实际页面查询连接器成功，IUnknown 身份一致，回调落到同一 Kotlin 实例 |
| P1 定制编译器发布包与 CI | C1 | 输入：fork 中的 Kotlin 后端及现有构建入口；输出：独立编译器发布包、版本清单、校验值和 Windows CI 工作流 | 干净 Windows 环境能用解压后的入口运行，产物不依赖开发 checkout；PR 只构建，发布由专用 tag/手动发布流程控制 |
| P2 插件工具解析 | P1 | 输入：固定编译器版本、host 架构、协议要求；输出：经校验的本地工具路径及匹配依赖 | 缓存/离线/并发解析可用，不兼容或下载失败明确报错；不自动调用机器上的 stock XamlCompiler |
| B1 Gradle 和资源 | A1、P2 | 输入：同目录文件与依赖、发布的定制编译器；输出：单一构建入口下的生成代码、XBF、PRI | 无依赖环、无逐文件配置，clean/增量/删除改名检查通过 |
| V0 Gallery 迁移清单 | G0，可提前只读盘点 | 输入：当前 Gallery 路由、页面、公共控件、资源和源码展示；输出：逐页迁移矩阵及现有行为基线 | 每条现有路由都有对应项；标明所需编译器能力与上游责任，禁止删路由缩小范围 |
| V1 Gallery 首个页面闭环 | B1、V0 | 输入：现有 `basicinput/ButtonPage.kt`；输出：同目录 `ButtonPage.xaml` + Kotlin 后置代码，经原 Gallery 导航打开 | 原按钮示例和交互保留，真实 XBF 加载、private 回调、命名控件、多实例与释放通过；只是中间里程碑 |
| V2 Gallery 壳层和公共 UI | V1 及所需上游能力 | 输入：MainWindow、页面宿主、公共示例容器和资源；输出：对应 XAML + Kotlin | 原导航、搜索、主题、窗口生命周期和公共布局保持，KSP 路由与双源码展示正常 |
| V3 Gallery 逐页迁移 | V2；每页先满足其上游依赖 | 按迁移矩阵每次迁移一页或一个紧密关联控件组；输出：该页 XAML、后置代码、实际交互验收 | 每页静态 UI/模板/样式迁入 XAML，行为保真；缺能力回上游补齐，禁止保留整页代码树伪称迁移完成 |
| V4 Gallery 最终验收 | 所有 V3 条目完成 | 输入：完整迁移后的现有 Gallery；输出：全路由巡检、交互和资源/增量构建结果 | 迁移矩阵无未处理页面；默认运行入口使用 XAML 路径；通过下文最终验收条件 |
| V5 Native / IDE 状态 | V4 | 输入：同一 Gallery 与共享契约；输出：目标分别验证结果和明确缺口 | 未验证就记未验证；不把 JVM 成功扩展成跨平台或 IDE 成功，保留 Gallery 已有双目标可行性 |

G1 只验证编译器工具能力，不设计 WinRT 语义，不把探针中的模型/桩搬进 production。正式实现仍按 runtime → metadata → generator/plugin → projection → authoring → sample 的依赖顺序进行。

### 已选定的实现策略，不再留给执行者临场选择

- **实例状态**：生成真实实例字段。初版命名元素对用户只读，内部连接器写入；加载前访问抛出包含页面/元素名的明确错误。不使用全局 Map，不对生成属性开放无约束 setter。
- **类成员增强**：FIR 生成用户可见声明和必要接口，IR 补实现。无注解筛选以输入索引匹配 ClassId；不要用运行时反射扫描页面。
- **事件**：初版仅普通实例处理函数，不支持重载歧义、泛型/suspend 处理函数或 `x:Bind` 事件表达式。签名必须与实际投影 delegate 兼容；支持 private，生成桥接留在用户类中。
- **初始化**：按用户追加要求对齐现代 C++/WinRT 的两阶段构造。编译器插件在完整 Kotlin 构造调用返回后调用 `initializeComponent()`，不能插入基类构造阶段；用户覆盖该方法并先调用 `super.initializeComponent()`，随后访问命名控件，不增加另一套初始化钩子。
- **失败语义**：加载或连接失败沿用现有异常/HRESULT 边界；不得吞掉失败或把失败后的对象标成成功加载。重入和失败后再次调用行为先按参考路径确定，再实现并验证。
- **身份**：连接器并入页面已有 CCW；辅助状态对象可以存在，但不能作为页面的替代 COM 对象。
- **第一版产物**：类内部声明/实现由插件生成；外部 `.kt` 仅用于独立辅助代码。XamlCompiler 输出稳定描述供插件消费，不输出第二份同名用户类。
- **工具分发**：定制 XamlCompiler 在 `compose-fluent/microsoft-ui-xaml` 的 Windows CI 构建并发布固定版本；Gradle 插件默认解析自己的兼容版本。应用项目不需要检出或现场编译该 fork。
- **不支持特性**：在编译期拒绝，禁止退化成运行时解析字符串 XAML、按名字反射找方法或跳过连接器。

### 固定构建图与循环依赖处理

按以下逻辑节点实现；实际 Gradle 任务名采用现有项目约定，以下不是已经存在的命令：

```text
依赖还原 / SDK 与投影元数据
  + 解析并校验 CI 发布的定制 XamlCompiler 与匹配的工具依赖
  → XAML 声明预分析（只依赖 XAML 与已知类型，不依赖最终用户二进制）
  → 临时 Kotlin 语义编译（FIR 消费声明索引，输出应用符号/metadata）
  → XamlCompiler 最终分析（消费应用符号，输出最终实现计划与 XBF）
  → 最终 Kotlin 编译（FIR 使用同一声明索引，IR 消费最终实现计划）
  → PRI / staging / WinUI Gallery 运行
```

- 临时语义编译使用独立目录和显式模式；生成方法可使用只供该模式编译的抛错占位体，产物不得被运行、打包或加入最终 classpath。
- 应用符号输出必须覆盖 private 处理函数所需信息；只把 WinRT 可表达部分交给现有 WinMD writer，其余保留在编译期符号附表。
- 最终实现计划携带声明索引指纹。发现两个阶段类型/声明不一致时构建失败并重建上游，禁止“再循环几次直到成功”。
- 若预分析无法仅凭 XAML 和依赖完成某个第一版必要声明，先输出最小复现及缺失输入；不要偷偷改成依赖最终 JAR，也不要另写一套 XAML parser。

### 必须交回设计的变化

只有以下情况需要暂停相关分支并请用户决定，不对已经明确的日常实现重复确认：需要 Kotlin fork；必须改用户继承关系；需要全量替换 XamlCompiler reflection/schema；无法在同一页面 CCW 暴露连接器；需要突破根 `PLAN.md` 的条目结构；需要放弃无注解规则。提交具体错误、参考位置和最小复现，不只说“无法实现”。

## 目标与推荐默认方案

- [x] 同目录下的 `MainPage.kt` 与 `MainPage.xaml` 自动关联，不需要类注解或逐文件 Gradle 配置。
- [x] 保留 XAML 的 `x:Class`，以完整限定名确认真实 Kotlin 类身份；文件主名只用于发现与一致性校验。
- [x] 用户继续声明 `class MainPage : Page()`；优先通过已有编译器插件补充声明和实现，不要求用户改成生成基类。
- [x] 使用标准 Kotlin 语法和 FIR/IR 插件扩展点，不增加 `partial` 关键字。
- [x] 保留 XamlCompiler 的 DOM、语义分析、connection ID 重写和原生 GenXbf 流程。
- [x] 把 ABI、对象身份、生命周期放在 runtime/authoring；生成器只生成描述、成员和调用，不在样例中补运行时机制。

第一版预期用户代码形态如下；类型导入及属性值装箱使用项目实际投影 API，示例不规定新的公共 API：

```kotlin
package sample.views

class MainPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
        // XAML 已加载，可访问命名控件；不在 init 中手动加载。
    }

    private fun onClick(sender: Any?, args: RoutedEventArgs) {
        // myButton 是 XAML 命名元素，由 FIR 提供声明、IR 提供实现。
        // 此处使用现有 Button 投影 API 更新内容。
    }
}
```

```xml
<Page
    xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
    xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
    x:Class="sample.views.MainPage">
    <Button x:Name="myButton" Click="onClick" Content="Click" />
</Page>
```

## 已核实的源码事实

以下是定位入口，不代表相关能力已完成端到端验证。执行前重新检查当前版本和工作区状态。

| 仓库 / 文件 | 已核实内容及意义 |
| --- | --- |
| Kotlin：`winrt-compiler-plugin/.../KotlinWinRTCompilerPlugin.kt` | `KotlinWinRTCompilerPluginRegistrar` 注册 `IrGenerationExtension`；当前主入口尚未注册 FIR 声明/父类型生成扩展。已有 authoring 元数据输出和 `lowerAuthoredTypeConstructors` 可复用。 |
| Kotlin：`gradle/libs.versions.toml` | 研究时 Kotlin 版本为 `2.4.0`；插件实现必须以项目实际版本的 API 为准。 |
| Kotlin：`winrt-runtime/.../WinUiAuthoredTypeMetadata.kt` | 仅提供代码创建控件的类型身份；`IsConstructible=false`，`ActivateInstance`/`CreateFromString` 返回 `E_NOTIMPL`，自定义成员尚未生成。 |
| Kotlin：`winrt-runtime/.../XamlSystemProjectionRuntime.kt` | 已有 Application 的 metadata provider 接入、SDK provider 转发及 authored 类型查询。 |
| Kotlin：`winrt-metadata/.../WinRTAuthoringMetadata.kt` | authored descriptor 描述类名、基类、接口、工厂，尚不包含完整 Kotlin 用户成员模型。 |
| Kotlin：`winrt-metadata/.../WinRTPortableExecutableMetadataWriter.kt` | 已有真实 PE/WinMD 输出，不能另造重复写入器。 |
| Kotlin：`winrt-compiler-plugin/.../authoring/KotlinWinRTAuthoringMetadataModel.kt` | 现有 authored WinMD 的输出桥梁。 |
| Kotlin：`windows-toolkit-gradle-plugin/.../GenerateWinRTProjectionsTask.kt` | 已有 authoring 扫描、元数据及 WinMD 生成步骤；公开候选类型导出规则不能直接等同于应用内部 XAML 类型规则。 |
| Kotlin：`windows-toolkit-gradle-plugin/.../StageWindowsPackageRuntimeAssetsTask.kt`、`StageWinAppPackageTask.kt` | 已有 runtime assets / PRI / metadata provider 相关流程，优先接入而非平行实现。 |
| XamlCompiler：`BuildTasks/Microsoft/Xaml/XamlCompiler/Language.cs` | 固定语言注册及各 pass 生成器；`IsNative = !IsManaged`。 |
| XamlCompiler：`BuildTasks/Microsoft/Lmr/XamlTypeUniverse.cs` | `IsManaged` 影响 .NET WinRT metadata projection；不能因为 Kotlin/JVM 有 GC 就按 C# managed 分支处理。 |
| XamlCompiler：`BuildTasks/CompileXamlInternal.cs` | 创建 schema、加载 `LocalAssembly`、调度两阶段代码和 XBF 生成。 |
| XamlCompiler：`BuildTasks/ConsoleCompileXaml.cs` | 支持 `XamlCompiler.exe input.json output.json`。 |
| XamlCompiler：`Exe/Microsoft.UI.Xaml.Markup.Compiler.MSBuildInterop/CompilerInputs.cs`、`CompilerOutputs.cs` | 输入含 XAML、references、LocalAssembly、pass、GenXbf；输出含代码/XAML/XBF 路径及诊断。 |
| XamlCompiler：`BuildTasks/Microsoft/Xaml/XamlCompiler/CodeGenerators/CppWinRT/CppWinRT_PagePass1.tt`、`CppWinRT_PagePass2.tt` | C++/WinRT 通过 `PageT<D>` 生成基类接入连接器；事件回调用实际派生类型，不能把 C# partial 当作唯一后端模型。 |
| XamlCompiler：`BuildTasks/Microsoft/Xaml/XamlCompiler/XamlConnectionIdRewriter.cs` | 剥离编译期绑定语法并写入 connection ID，生成连接器必须与该输出一致。 |
| XamlCompiler：`BuildTasks/Microsoft/Xaml/XBF/XbfGenerator.cs` | 根据编译器进程架构选择原生 GenXbf DLL，保留此实现。 |

上表 Kotlin 的 `...` 指文件现有 package 路径，执行时通过 CodeGraph 定位。若仓库有 `.codegraph/`，先用 CodeGraph；索引未覆盖或返回不相关代码时，再做明确范围的 `rg` 查找。

## 编译链路首个里程碑与最终交付范围

- [x] Windows JVM：从原始 `.xaml` 构建出 XBF、生成成员及 PRI，并在原 WinUI Gallery 导航中加载迁移后的 Button 页面。
- [x] 页面保持直接继承现有 WinUI 投影基类；不需要注解或手写连接器。
- [x] `x:Name` 在用户 Kotlin 源码中可解析、类型检查通过，加载后引用真实控件。
- [x] 普通 XAML 事件连接到用户的 private 实例处理方法。
- [x] 生成连接器通过页面自身 COM 身份可查询；关闭页面后回调和引用按现有生命周期约定释放。
- [x] 清洁构建、无改动重建、修改/删除/重命名 XAML 均有正确结果。

以上是中间里程碑，不能据此结束任务。最终交付必须完成现有 Gallery 的 XAML + Kotlin 迁移并保持原有页面、资源与交互。首个里程碑暂不要求完整 `x:Bind`、任意 Kotlin ViewModel、自定义标记类型激活、DataTemplate 绑定作用域或 `x:Load`；但 Gallery 迁移或其专门演示页面实际需要的能力属于本任务后续必要切片，必须先回到对应上游模块实现，再迁移使用方。热重载和设计器仍在后续范围。命中尚未实现的特性时明确诊断，禁止静默忽略。无 `x:Class` 的资源字典作为资源处理，不能误关联到 Kotlin 类。

## 阶段 0：重新确认前置条件与固定责任边界

- [x] 阅读两仓库适用的 AGENTS；查看 `git status`、现有分支及活跃工作，保护用户和其他任务改动。
- [x] 阅读 `.cswinrt/src/WinRT.Runtime/ComWrappersSupport.cs` 的接口表、`.cswinrt/src/cswinrt/code_writers.h` 的 composable 构造、`.cswinrt/src/Authoring/WinRT.SourceGenerator/WinRTTypeWriter.cs` 的类型/成员 authoring。
- [x] 检查当前投影是否已生成 `IComponentConnector`、`IXamlMetadataProvider`、`IXamlType`、`IXamlMember`、`Application.LoadComponent`，区分缺失投影与缺失 runtime 能力。
- [x] 用项目 Kotlin 版本核实 FIR 成员生成、额外接口及 IR 实现 API；检查现有插件如何接入 JVM 与 Native。
- [x] 固定 XAML 与投影之间的名称映射输入：WinRT 全名、Kotlin FQN、投影属性/事件 API、源位置。复用现有规范化模型，禁止在 C# 后端另写一套推测性 Kotlin 类型映射。
- [x] 确认 XamlCompiler 当前可用构建入口和 GenXbf 依赖。先使用现有构建配置；不可为了研究目标安装无关工具或重写整个 WinUI 构建系统。
- [x] 核实 fork 的 remote、现有 `.github/workflows/pr-build.yml` 和发布权限/惯例，定位能够独立打包的编译器构建产物；不假设需要构建整个 WinUI 仓库。
- [x] 只读盘点现有 `winui-gallery`，建立 V0 迁移矩阵；以实际导航注册与页面工厂为准，不把 README 的历史页数当作完整清单。此时不提前改 UI。

验收：能够指出每个缺口的所属模块和参考源码。此阶段不先扩展 gallery 或手写投影。

## 阶段 1：runtime 的 XAML 契约与生命周期

- [x] 补齐最小连接器调用、对象转换及 WinRT 返回值所有权契约；沿用既有 GUID、vtable、HRESULT、委托和 CCW 机制。
- [x] 明确页面的 composed outer/inner 身份：`LoadComponent` 操作的页面与 `QueryInterface(IComponentConnector)` 返回的接口必须属于同一 COM 对象身份。
- [x] 支持每个页面独立的命名元素存储与加载状态；不要用无生命周期约束的全局强引用 Map 模拟实例字段。
- [x] 明确事件订阅、弱引用/强引用和撤销责任；复用已有 event token、delegate 与 disposal 基础设施。
- [x] 定义页面初始化的安全调用时机及重复调用行为。检查现有 `lowerAuthoredTypeConstructors`，避免插件随意在基类构造阶段加载 XAML，造成派生对象未初始化就回调。
- [x] 为后续生成式 metadata 描述建立最小共享契约；第一版只实现实际所需能力，不能把类型身份 provider 标成完整 markup activation 支持。
- [x] 添加针对身份、指针所有权、事件回调和加载状态的必要 runtime 验证，记录对应 CsWinRT 源码映射。

验收：在不依赖业务样例补丁的前提下，上游 ABI/身份契约可支撑连接器和加载。若现有契约已经满足，复用并验证，不新建重复抽象。

## 阶段 2：metadata、XAML 索引与编译阶段协议

- [x] 设计有 schema version 的确定性 XAML 索引，至少包含相对资源路径、`x:Class`、根类型、命名元素及类型、事件名称/处理函数名、源位置和功能标记。
- [x] Kotlin 类型描述保留基类、接口、属性/方法签名、可见性、可空性及 WinRT/Kotlin 名称映射。编译期应用类型与公开 WinRT 组件导出必须区分。
- [x] 扩展现有 PE/WinMD writer 的必要成员形状；优先验证真实 XamlCompiler loader 对现有 authored WinMD 的读取，不能仅以 Kotlin 自己能读回为验收。
- [x] 私有事件处理函数等 Kotlin 特有符号通过附加描述保留，不为满足编译器反射而自动导出为公共 WinRT ABI。
- [x] 第一版优先解决页面与普通事件所需的有限 schema 适配。不要提前重写整个 `System.Reflection` / `System.Xaml` 类型体系；任意 ViewModel 与完整绑定留在后续范围。
- [x] 固定无循环的阶段协议：XAML 结构预分析产生 FIR 所需声明输入；Kotlin 语义分析产出应用符号；最终 XAML 分析产生连接/实现计划与 XBF；最终 Kotlin 编译消费稳定计划。
- [x] 预分析优先复用 XamlCompiler Pass 1 的 DOM/harvester。若需要新的分析模式，在编译器现有管线内增加明确模式，不在 Gradle 里另建 XAML 语义解析器。
- [x] 按执行协议实现临时 Kotlin 语义编译，明确其生成声明如何获得占位实现、输出目录如何隔离，以及最终产物不得包含占位实现。不能简单让主编译任务依赖自己的输出。

验收：最小页面的索引与类型输入可重现，XamlCompiler 能正确辨认页面和事件签名，输入删除后不存在陈旧符号。

## 阶段 3：Kotlin 语言后端与 FIR/IR 集成

### XamlCompiler 后端

- [x] 在 `Language.cs` 注册 Kotlin 与各 pass 输出策略。将 managed metadata projection、C++ 专用路径等职责从简单的 `IsManaged/IsNative` 二分中窄范围解耦；保留其他语言既有行为。
- [x] 接入 `XamlCodeGenerator`；为 `ICodeGenOutput`、`LanguageSpecificString`、`TypeForCodeGen`、`XamlSchemaCodeInfo` 增加 Kotlin 所需输出。
- [x] 集中处理类型名、泛型、数组、转义、字面量、可空性、cast 与事件调用，使用阶段 2 的投影映射。不得把 C# `global::`、CLR collection 或 C++ 类型语法直接带入 Kotlin。
- [x] 复用 connection ID rewriter；生成声明索引与最终连接计划，并按需输出 Kotlin 辅助源码。不要在外部 `.kt` 中再次声明用户类来伪装 partial。
- [ ] skipped: T4 如被使用，确认 `.tt` 与纳入编译的生成 `.cs` 同步方式，并更新相应 project includes；禁止只改模板却留下旧编译实现。

### kotlin-winrt 编译器插件

- [x] 在现有 registrar 中增加 FIR registrar；以索引中的完整类名筛选目标，而非依赖注解 predicate。
- [x] 用 `FirDeclarationGenerationExtension` 提供命名元素属性、初始化方法及连接器方法声明。
- [x] 用 `FirSupertypeGenerationExtension` 加入必要接口；处理用户已声明接口、override 与成员冲突，不盲目重复插入。
- [x] 用 FIR checker 提供 `x:Class` 不匹配、基类冲突、重复命名、事件签名错误等诊断；诊断尽量带 XAML 文件与行列信息。
- [x] 在现有 IR 管线为 FIR 生成声明补字段、访问器和方法体；给生成声明使用可识别 origin/key，确保重复运行和不同插件不会互相覆盖。
- [x] 对 private 事件处理函数生成位于用户类内部的调用桥接，或在同类生成方法体内直接调用正确 IR symbol；不扩大用户成员可见性，不使用反射绕过。
- [x] 接入现有 authoring 信息收集顺序，使新增接口和成员可被后续 CCW 描述看到；明确 FIR 元数据、IR 与 WinMD 输出之间的时序。
- [x] 构造后自动调用 `initializeComponent()`；核实 runtime/composition 已建立回调所需身份，禁止在基类或尚未完成的派生构造阶段加载 XAML。验证普通调用、构造函数引用、次构造函数、跨模块消费、authoring 激活、初始化异常与生成加载逻辑的幂等性；示例覆盖 `initializeComponent()` 并先调用 `super.initializeComponent()`。
- [x] JVM 与 Native 共享生成语义；平台限制放到现有 target adaptation。生成实现不得依赖 JVM 反射或仅写 `.class` 的旁路作为永久架构。

验收：用户 Kotlin 函数能引用 XAML 生成成员，private 事件桥接能编译，无注解、无生成基类、无 Kotlin fork；生成器回归证明原有 C#/C++ 相关行为未被破坏。

## 阶段 4：定制编译器 CI 发布、插件消费与运行集成

### P1：fork 仓库构建和发布

默认分发渠道为维护仓库的 GitHub Releases，发布自带工具清单的归档包。若该仓库已有满足固定版本/校验/长期下载要求的发布机制，复用它并在插件中统一解析，不能同时维护两套默认渠道。具体 tag 和 asset 命名在实现时按仓库现有规范固定，不猜测尚不存在的下载地址。

- [x] 在 `compose-fluent/microsoft-ui-xaml` 增加专用于定制 XamlCompiler 的 Windows 工作流，复用现有依赖还原和编译入口，构建 `src/XamlCompiler` 所需范围。
- [x] PR 工作流生成可下载的验证 artifact；正式分发通过专用版本 tag 或显式手动发布工作流进入 GitHub Release。Gradle 默认消费长期保留的 Release asset，不能依赖会过期的 Actions artifact。
- [x] 发布包包含可运行入口、必要 managed/native 依赖、工具清单、许可/归属文件和 SHA-256 校验清单；输出可追溯的 fork commit、上游版本及工具版本。运行期临时输出不混入发布包。
- [x] 工具清单明确：schema version、Kotlin XAML 协议版本、支持的 feature 列表、host OS/架构、入口相对路径、运行方式和运行前置条件，以及适配的 WinUI/GenXbf 依赖范围。工具版本与协议版本分别管理。
- [x] 优先形成自包含工具包；若现有构建要求外部 .NET runtime，先在清单中声明并由解析器检测，不能留下只有 CI 构建机才能运行的隐含依赖。先覆盖实际验证过的 host 架构，再扩展其他包。
- [x] GenXbf 来自与所选 Windows App SDK/WinUI 配套的官方依赖或允许分发的构建产物：明确来源、版本与解析方法。若不随包分发，由 Gradle 使用现有依赖解析获得并传入路径；禁止从开发机任意安装目录挑一个 DLL。
- [x] CI 在不含开发仓库绝对路径的干净目录解包运行入口，验证协议识别、最小 Kotlin XAML 编译及 XBF 输出；发布只在该检查通过后进行。
- [x] 正式版本按不可变内容管理，修复通过新版本发布；仓库来源、tag、包校验和均可追溯。首次发布的实际 URL/版本记录为插件消费输入。

### P2：Kotlin/WinRT Gradle 插件消费

工具下载/解包由 `windows-toolkit-gradle-plugin` 负责，FIR/IR 编译器插件只消费 Gradle 传入的索引和计划，不在编译期间联网。

- [x] 在现有 Windows tooling 配置下增加定制 XamlCompiler 的版本与本地路径覆盖项，复用现有工具解析/缓存设施。固定一个随插件发布并验证过的默认版本，不使用 `latest` 或动态版本范围。
- [x] 插件维护默认版本的 manifest/asset 校验信息，明确所需协议和 feature；应用显式覆盖版本时仍必须通过协议/能力与依赖兼容检查，不因版本数字较新就假定兼容。
- [x] 工具按编译任务的 Windows host 架构解析。即使目标为 `mingwX64`，也不能用目标信息代替 host 信息选择编译器或其 GenXbf。
- [x] 用执行期任务/服务解析工具；配置期不联网。缓存 key 至少包含来源、版本、host 架构及内容校验，支持原子下载/解包、并发构建、失败重试及已有缓存下的离线构建。
- [x] 在调用前验证 manifest、入口文件及兼容依赖；缺工具、网络失败、校验不符或协议不兼容明确失败。不得静默回退到 Windows SDK/NuGet 自带的不支持 Kotlin 的 stock XamlCompiler。
- [x] 提供明确的本地开发 override，让 fork checkout 的编译输出可用于迭代；它使用同样的协议校验和任务输入指纹，不能作为发布插件的默认值，也不能把 `I:\\...` 路径写入产物。
- [x] 将实际工具内容/版本、GenXbf 版本、协议和相关 feature 纳入 XAML 编译任务输入；工具变更必须使对应缓存失效。已发布编译器不得被打包进 Gallery 的应用运行时。
- [x] 至少一次跨仓库验收：从实际 CI Release 获取工具，在无 fork checkout 的 Windows 环境构建迁移后的 Gallery。使用本地 override 成功只能作为开发验证，不能代替发布链路验收。

### projection、authoring 与 Gradle/资源接入

- [x] 仅通过现有生成器产出缺失投影，不手写扩张 `winrt-projections`。
- [x] 把生成连接器方法接入页面现有 TypeDetails/CCW，验证接口查询与原页面身份一致；仅给 Kotlin 类加接口不等于完成 ABI 支持。
- [x] 接入 Application metadata provider 的应用类型查询及 SDK fallback。若第一版不实现自定义类型激活，明确诊断其使用，不用激活基类替代派生类。
- [x] 在现有 Gradle 插件中添加 XAML discovery、预分析、语义输入、最终编译任务，使用结构化 `input.json`/`output.json` 协议。
- [x] 第一版同目录规则：每个参与当前 compilation 的 `.kt` 文件发现同主名 `.xaml`，要求 `x:Class` 对应该文件中唯一目标类；多类文件可以存在，但必须无歧义。平台/source-set 选择使用 Gradle 实际 compilation 输入。
- [x] 大小写、规范化路径、同名异包、重复 `x:Class`、跨 source-set 重复资源必须有确定规则与错误信息。不要通过任意文件系统顺序选一个候选。
- [x] 声明 XAML、Kotlin 符号输入、WinMD、投影映射、工具版本、语言目标和资源配置为必要任务输入，隔离 JVM/Native 产物及缓存。
- [x] 通过工具输出清单管理生成文件；删除/改名 XAML 时清除仅由该任务拥有的旧产物。不得清空共享生成目录或用户源码。
- [x] 复用现有 makepri、staging 和 bootstrap；保证 XAML 逻辑 URI、XBF 文件与 PRI 索引一致。选择与编译器进程架构匹配的 GenXbf，勿误用应用目标架构。
- [x] CLI 诊断必须传播失败码与 XAML 位置；不能把缺少 compiler/GenXbf/PRI 处理为成功跳过。

验收：一个正常 Gradle 构建入口可自动解析定制编译器并完成整个流程，无逐文件手动配置；clean、增量和已有缓存的离线构建均正确。编译器版本与插件版本的兼容关系可查。

## 阶段 5：现有 WinUI Gallery 迁移与端到端验收

最终验证载体为现有 `:winui-gallery`。底层可以保留必要的小型 fixture，但不新建独立演示应用替代 Gallery 迁移。先迁移一个真实页面，再迁移壳层/公共 UI，最后覆盖所有现有页面。用户授权的是现有 Gallery 的迁移，不能将其缩成只有几个控件的新 Gallery。

### 已定位的迁移入口

共享 UI 源码根为 `winui-gallery/src/winuiMain/kotlin/io/github/composefluent/winrt/gallery`。下列位置已读到或定位到；执行前确认最新源码，不照搬 README 中可能过时的注解名或页数。

| 位置 | 迁移责任 |
| --- | --- |
| `Main.kt` 中的 `GalleryApplication`、现有 `MainWindow` | Application/Window 的资源和静态 UI 转为 XAML；启动、窗口管理、通知和激活逻辑留在 Kotlin；必要时按类名拆文件以满足同主名规则 |
| `basicinput/ButtonPage.kt` | 第一个真实迁移对象；保留文字按钮、图片按钮、内置样式、长文本及禁用/点击反馈示例 |
| `GalleryPageHost.kt`、`GalleryNavigationHost.kt`、`GalleryPageHeader.kt`、公共 ExamplePage/示例容器 | 接入新页面实例和 XAML 公共布局；保留现有 route、导航语义和主题行为 |
| `GalleryAnnotations.kt`、`GalleryCatalog.kt`、`winui-gallery/processor` | 保留现有导航与示例注册契约；补充 XAML/Kotlin 两份源码的发现与展示，不将 KSP 用作 XAML 编译器 |
| `fundamentals`、`collections`、`styles` 等分类 | 逐项列明资源、样式、模板、绑定、自定义控件等必要上游能力，再按依赖顺序迁移 |
| `code`、现有源码展开/复制功能 | 按上游 `ControlExample.SampleDefinition` 独立组织每个 example 的 XAML/Kotlin 片段，正确切换、复制与着色；不得给每个 example 重复展示整页文件，不需要 Kotlin 的示例仅显示 XAML |
| `winui-gallery/build.gradle.kts`、`src/winuiMain/appxResources`、`README.md` | 接入 XAML 构建、XBF/PRI 和打包，更新使用说明及实际验证状态 |

### 每页迁移规则（Luna 按此逐页执行）

- [x] 迁移矩阵至少包含：route、现有工厂/类文件、目标 XAML/类名、原有示例和交互、上游能力依赖、迁移状态、JVM/Native 验收状态。记录在本地执行状态文档中，所有现有页面均须覆盖。
- [x] 尽量完整保留 WinUI Gallery 上游 XAML 的静态可视树、布局、样式、资源与 `ControlExample` 组织方式，只修改 Kotlin 类型/处理函数映射等必要适配；缺少自定义控件或绑定能力时先补上游支持，不把展开包装布局作为最终方案。Kotlin 保留事件处理、状态、数据、导航及确实需要过程式 API 的行为。程序化动画、系统集成或刻意演示动态创建的局部代码可以保留，但逐项说明原因，不能用来保留整页纯代码布局。
- [x] 为函数式页面引入与 `.xaml` 同主名的实际 code-behind 类，选择满足现有宿主契约的 Page/UserControl 等根类型。`@GalleryPage` 同时支持无参数构造的 `UIElement` 子类，直接注册页面类并由 KSP 生成构造调用，不要求额外的包装工厂；保留已有函数注册兼容，迁移后的旧工厂不得继续重复构建同一可视树。
- [x] 现有公共容器需要 XAML 自定义类型支持时，先补全 authoring/metadata/generator 契约，不在 Gallery 写自定义 XAML 加载器或手工 vtable。
- [x] 不改 route、分组、标题、示例数和数据语义来减少迁移工作；不为所有页面新增 `@XamlPage`。Gallery 原有导航注解可保留，它们不承担 XAML 关联责任。
- [ ] doing: 每页至少通过原有入口打开，检查其所有示例和交互后再标记完成。发现缺少模板/绑定/资源能力时，暂停该页迁移、完成对应上游切片，再返回；缺项不能作为最终保留纯代码整页的理由。
- [x] 每完成一页或一个共享依赖不可分割的小组即提交；允许迁移期间新旧页面共存，最终默认入口不得用旧实现 fallback 掩盖 XAML 失败。

### 最终验收条件

- [x] V0 矩阵中所有现有页面均完成迁移；如运行前现有页面存在问题，先记录基线并区分迁移回归，不把原有未验证状态写成已验证。
- [x] Windows JVM 以 Gallery 原启动方式实际打开窗口，默认页面/导航均使用 XAML + Kotlin；点击按钮进入 Kotlin private 方法并更新命名控件，不能只以编译通过或进程存在判定成功。
- [ ] 遍历全部现有路由，逐页验证交互；壳层同时覆盖搜索、收藏/最近记录、主题切换、窗口尺寸、键盘导航，以及原已实现的深链/通知等入口。
- [ ] 对迁移前后界面进行视觉检查：布局、间距、滚动、文本、图标、资源、明暗主题及高对比度行为保持；不得通过删内容或改成占位布局通过验收。
- [x] 资源/样式/模板/绑定专门演示页使用对应 XAML 能力，源码展开同时呈现实际 XAML 和 Kotlin；演示文案必须与实际执行路径一致。
- [x] 最终 Gallery 构建使用 fork 的 CI 发布版本，关闭本地工具 override；记录实际编译器版本/协议/校验值，确认源码中没有开发 checkout 路径依赖。
- [ ] doing: 验证同页多个实例各自持有状态；重复初始化无重复订阅；关闭与回收不出现悬挂回调或明显引用滞留。
- [x] 验证无改动重建、改变控件类型/名称、改变事件签名、删除和重命名 XAML 的正确增量行为。
- [x] 验证诊断至少覆盖：错误 `x:Class`、基类不兼容、成员冲突、错误处理函数签名，以及当前仍未支持的语法；如已支持某种绑定，其用例必须验证成功而非保留“预期失败”。
- [x] 按 runtime → metadata → generator/plugin → Gradle → sample 顺序执行针对性验证，避免每次全量构建。
- [x] 在 Windows 对 `mingwX64` 分别验证插件声明/IR 与迁移后的 Gallery；保留现有共享 source set。若 Native 尚未通过，明确列出缺口，只报告 JVM 迁移已验证，不宣称完整双目标完成。
- [x] 单独评估 IDE 对无注解 FIR 生成成员的识别。Gradle 编译成功不等于补全和跳转成功；如需要 IDE 插件接入，记录实际支持状态，不宣称已具备完整 IDE 体验。

## 推荐提交边界与交付

遵守适用 AGENTS 的原子提交要求，完成一个可验证切片后再进入下一个无关切片。只暂存本任务文件，不包含工作区其他修改。跨仓库提交分别进行，在交接说明中记录配套提交关系，不自动推送。

1. runtime 的 XAML 连接器/生命周期契约与验证。
2. metadata/应用符号描述与 WinMD 适配。
3. XamlCompiler Kotlin 后端及配套的 FIR/IR 支持；两个仓库记录兼容协议版本。
4. fork 的编译器发布包/CI、Kotlin 插件的固定版本解析分别提交，再完成 authoring 与 Gradle/XBF/PRI 集成。
5. Gallery 首个页面、壳层/公共 UI，以及随后每页或相关小组的独立迁移提交；中途必需的上游能力按其所属模块单独提交。
6. Gallery 全量验收、源码展示、构建/打包及使用文档收尾。

最终向用户报告：两个仓库提交、CI Release 与实际编译器版本/协议、插件消费配置、Gallery 逐页迁移清单、实际 XAML/Kotlin 示例、构建和启动入口、已运行的视觉/交互验证、JVM/Native/IDE 各自状态及剩余不支持功能。只有最小页面成功时报告中间里程碑，不结束为“任务完成”。不得将本文所有勾选项一并标成完成，除非证据实际满足。

## 按 Gallery 需求推进的能力与可延后范围

下面前五项中 Gallery 迁移实际需要的部分已经包含在本次最终目标内，按依赖关系在迁移对应页面前补齐；超出 Gallery 需求的通用化再单独推进。不得以本节的“后续”性质跳过 Gallery 必需功能。

- [ ] doing: 自定义 Kotlin XAML 类型构造及完整 `IXamlType`/`IXamlMember` 描述。
- [ ] doing: `x:Bind OneTime`，随后 OneWay/TwoWay、INPC、集合通知、转换器及更新生命周期。
- [ ] doing: DataTemplate 的独立 connector/binding scope、`x:Load` 和延迟创建/卸载。
- [ ] doing: 任意 Kotlin ViewModel 的 schema 适配，不强制其导出为 WinRT 组件。
- [ ] doing: App.xaml、资源库和跨模块 provider 的完整覆盖。
- [ ] 更完整的 IDE 支持、导航、设计器与热重载；均不作为第一版隐含承诺。

## 外部参考

- Kotlin 官方插件扩展与 IDE 边界：<https://kotlinlang.org/docs/custom-compiler-plugins.html>
- Kotlin 官方 FIR 插件说明：<https://github.com/JetBrains/kotlin/blob/master/docs/fir/fir-plugins.md>
- C++/WinRT 页面结构：<https://learn.microsoft.com/en-us/windows/uwp/get-started/create-a-basic-windows-10-app-in-cppwinrt>
- C++/WinRT 初始化生命周期说明：<https://github.com/microsoft/cppwinrt/blob/master/nuget/readme.md>

以上在线文档可能领先于仓库版本。实现以项目 Kotlin 版本对应的编译器源码/API、当前 XamlCompiler 和本地 `.cswinrt` 为准。
