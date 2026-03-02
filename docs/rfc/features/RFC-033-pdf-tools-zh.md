# RFC-033: PDF 工具

## 文档信息
- **RFC ID**: RFC-033
- **关联 PRD**: [FEAT-033 (PDF 工具)](../../prd/features/FEAT-033-pdf-tools.md)
- **关联架构**: [RFC-000 (总体架构)](../architecture/RFC-000-overall-architecture.md)
- **关联 RFC**: [RFC-004 (工具系统)](RFC-004-tool-system.md)
- **创建日期**: 2026-03-01
- **最后更新**: 2026-03-01
- **状态**: 草稿
- **作者**: TBD

## 概述

### 背景

AI 智能体经常需要处理 PDF 文档——总结报告、从发票中提取数据、阅读研究论文或分析扫描表单。目前，OneClawShadow 没有内置的 PDF 文件读取能力。用户可以附加文件（FEAT-026）并浏览文件系统（FEAT-025），但智能体无法从 PDF 中提取内容。

OneClaw 1.0 在 `lib-pdf` 中拥有成熟的 PDF 工具实现，提供三个工具：`pdf_info`、`pdf_extract_text` 和 `pdf_render_page`。本 RFC 将该功能移植到 OneClawShadow 的工具架构中，作为 Kotlin 内置工具，并对代码进行适配，使其使用 OneClawShadow 的 `Tool` 接口、`ToolResult` 和 `ToolDefinition` 数据类型。

### 目标

1. 实现三个 Kotlin 内置工具：`PdfInfoTool`、`PdfExtractTextTool`、`PdfRenderPageTool`
2. 创建共享工具类 `PdfToolUtils`，用于路径解析和页面范围解析
3. 向项目添加 PDFBox Android 依赖
4. 在应用启动时初始化 PDFBox
5. 在 `ToolModule` 中注册全部三个工具
6. 为所有工具和工具类添加单元测试

### 非目标

- PDF 创建、编辑或注释
- 扫描版 PDF 的 OCR（具备视觉能力的模型可分析渲染后的图像）
- 密码保护 PDF 的支持（推迟至后续迭代）
- PDF 表单交互
- PDF 转 Markdown（超出原始文本提取范围）

## 技术设计

### 架构概览

```
┌──────────────────────────────────────────────────────────────┐
│                     Chat Layer (RFC-001)                      │
│  SendMessageUseCase                                          │
│       │                                                      │
│       │  tool call: pdf_info / pdf_extract_text /            │
│       │             pdf_render_page                           │
│       v                                                      │
├──────────────────────────────────────────────────────────────┤
│                   Tool Execution Engine (RFC-004)             │
│  executeTool(name, params, availableToolIds)                 │
│       │                                                      │
│       v                                                      │
│  ┌────────────────────────────────────────────────────────┐  │
│  │                    ToolRegistry                         │  │
│  │                                                        │  │
│  │  ┌─────────────┐  ┌──────────────────┐  ┌───────────┐ │  │
│  │  │  pdf_info    │  │ pdf_extract_text │  │pdf_render │ │  │
│  │  │(PdfInfoTool) │  │(PdfExtractText  │  │  _page    │ │  │
│  │  │             │  │        Tool)     │  │(PdfRender │ │  │
│  │  │             │  │                  │  │ PageTool) │ │  │
│  │  └──────┬──────┘  └────────┬─────────┘  └─────┬─────┘ │  │
│  │         │                  │                   │       │  │
│  │         v                  v                   v       │  │
│  │  ┌─────────────────────────────────────────────────┐   │  │
│  │  │                  PdfToolUtils                    │   │  │
│  │  │  - initPdfBox(context)                          │   │  │
│  │  │  - parsePageRange(spec, totalPages)             │   │  │
│  │  └─────────────────────────────────────────────────┘   │  │
│  │         │                  │                   │       │  │
│  │         v                  v                   v       │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐  │  │
│  │  │ PDFBox       │  │ PDFBox       │  │ Android     │  │  │
│  │  │ PDDocument   │  │ PDFText      │  │ PdfRenderer │  │  │
│  │  │ .docInfo     │  │ Stripper     │  │ + Bitmap    │  │  │
│  │  └──────────────┘  └──────────────┘  └─────────────┘  │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

### 核心组件

**新增：**
1. `PdfInfoTool` -- 读取 PDF 元数据的 Kotlin 内置工具
2. `PdfExtractTextTool` -- 从 PDF 提取文本的 Kotlin 内置工具
3. `PdfRenderPageTool` -- 将 PDF 页面渲染为 PNG 图像的 Kotlin 内置工具
4. `PdfToolUtils` -- 用于 PDFBox 初始化和页面范围解析的共享工具类

**修改：**
5. `ToolModule` -- 注册三个 PDF 工具
6. `build.gradle.kts` -- 添加 PDFBox Android 依赖

## 详细设计

### 目录结构（新增与变更的文件）

```
app/src/main/
├── kotlin/com/oneclaw/shadow/
│   ├── tool/
│   │   ├── builtin/
│   │   │   ├── PdfInfoTool.kt              # NEW
│   │   │   ├── PdfExtractTextTool.kt       # NEW
│   │   │   ├── PdfRenderPageTool.kt        # NEW
│   │   │   ├── WebfetchTool.kt             # unchanged
│   │   │   ├── BrowserTool.kt              # unchanged
│   │   │   ├── LoadSkillTool.kt            # unchanged
│   │   │   ├── CreateScheduledTaskTool.kt  # unchanged
│   │   │   └── CreateAgentTool.kt          # unchanged
│   │   └── util/
│   │       ├── PdfToolUtils.kt             # NEW
│   │       └── HtmlToMarkdownConverter.kt  # unchanged
│   └── di/
│       └── ToolModule.kt                   # MODIFIED

app/src/test/kotlin/com/oneclaw/shadow/
    └── tool/
        ├── builtin/
        │   ├── PdfInfoToolTest.kt           # NEW
        │   ├── PdfExtractTextToolTest.kt    # NEW
        │   └── PdfRenderPageToolTest.kt     # NEW
        └── util/
            └── PdfToolUtilsTest.kt          # NEW
```

### PdfToolUtils

```kotlin
/**
 * Located in: tool/util/PdfToolUtils.kt
 *
 * Shared utilities for PDF tools: PDFBox initialization
 * and page range parsing.
 */
object PdfToolUtils {

    private const val TAG = "PdfToolUtils"
    private var initialized = false

    /**
     * Initialize PDFBox resource loader. Must be called once
     * before any PDFBox operations. Safe to call multiple times.
     */
    fun initPdfBox(context: Context) {
        if (!initialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
            Log.i(TAG, "PDFBox initialized")
        }
    }

    /**
     * Parse a page range specification string.
     *
     * Supported formats:
     * - Single page: "3"
     * - Range: "1-5"
     * - Comma-separated: "1,3,5-7"
     *
     * @param spec Page range specification string
     * @param totalPages Total number of pages in the document
     * @return Pair of (startPage, endPage) 1-based inclusive, or null if invalid
     */
    fun parsePageRange(spec: String, totalPages: Int): Pair<Int, Int>? {
        val trimmed = spec.trim()

        // Comma-separated: find overall min and max
        if (trimmed.contains(",")) {
            val parts = trimmed.split(",").map { it.trim() }
            var min = Int.MAX_VALUE
            var max = Int.MIN_VALUE
            for (part in parts) {
                val range = parsePageRange(part, totalPages) ?: return null
                min = minOf(min, range.first)
                max = maxOf(max, range.second)
            }
            return Pair(min, max)
        }

        // Range: "start-end"
        if (trimmed.contains("-")) {
            val parts = trimmed.split("-", limit = 2)
            val start = parts[0].trim().toIntOrNull() ?: return null
            val end = parts[1].trim().toIntOrNull() ?: return null
            if (start < 1 || end > totalPages || start > end) return null
            return Pair(start, end)
        }

        // Single page
        val page = trimmed.toIntOrNull() ?: return null
        if (page < 1 || page > totalPages) return null
        return Pair(page, page)
    }
}
```

### PdfInfoTool

```kotlin
/**
 * Located in: tool/builtin/PdfInfoTool.kt
 *
 * Reads PDF metadata: page count, file size, title, author,
 * subject, creator, producer, and creation date.
 */
class PdfInfoTool(
    private val context: Context
) : Tool {

    companion object {
        private const val TAG = "PdfInfoTool"
    }

    override val definition = ToolDefinition(
        name = "pdf_info",
        description = "Get metadata and info about a PDF file. " +
            "Returns page count, file size, title, author, and other document properties.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "path" to ToolParameter(
                    type = "string",
                    description = "Path to the PDF file"
                )
            ),
            required = listOf("path")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 15
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val path = parameters["path"]?.toString()
            ?: return ToolResult.error("validation_error", "Parameter 'path' is required")

        val file = File(path)
        if (!file.exists()) {
            return ToolResult.error("file_not_found", "File not found: $path")
        }
        if (!file.canRead()) {
            return ToolResult.error("permission_denied", "Cannot read file: $path")
        }

        PdfToolUtils.initPdfBox(context)

        return try {
            val doc = PDDocument.load(file)
            val result = try {
                val info = doc.documentInformation
                val lines = mutableListOf<String>()
                lines.add("File: ${file.name}")
                lines.add("Path: $path")
                lines.add("Pages: ${doc.numberOfPages}")
                lines.add("File size: ${file.length()} bytes")

                info.title?.takeIf { it.isNotBlank() }?.let {
                    lines.add("Title: $it")
                }
                info.author?.takeIf { it.isNotBlank() }?.let {
                    lines.add("Author: $it")
                }
                info.subject?.takeIf { it.isNotBlank() }?.let {
                    lines.add("Subject: $it")
                }
                info.creator?.takeIf { it.isNotBlank() }?.let {
                    lines.add("Creator: $it")
                }
                info.producer?.takeIf { it.isNotBlank() }?.let {
                    lines.add("Producer: $it")
                }
                info.creationDate?.time?.let {
                    lines.add("Created: $it")
                }

                ToolResult.success(lines.joinToString("\n"))
            } finally {
                doc.close()
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read PDF info: $path", e)
            ToolResult.error("pdf_error", "Failed to read PDF info: ${e.message}")
        }
    }
}
```

### PdfExtractTextTool

```kotlin
/**
 * Located in: tool/builtin/PdfExtractTextTool.kt
 *
 * Extracts text content from PDF files using PDFBox's PDFTextStripper.
 * Supports page range selection and output truncation.
 */
class PdfExtractTextTool(
    private val context: Context
) : Tool {

    companion object {
        private const val TAG = "PdfExtractTextTool"
        private const val DEFAULT_MAX_CHARS = 50_000
    }

    override val definition = ToolDefinition(
        name = "pdf_extract_text",
        description = "Extract text content from a PDF file. " +
            "Supports page range selection. For scanned PDFs with no text layer, " +
            "use pdf_render_page to get page images instead.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "path" to ToolParameter(
                    type = "string",
                    description = "Path to the PDF file"
                ),
                "pages" to ToolParameter(
                    type = "string",
                    description = "Page range to extract (e.g. \"1-5\", \"3\", \"1,3,5-7\"). " +
                        "Omit to extract all pages."
                ),
                "max_chars" to ToolParameter(
                    type = "integer",
                    description = "Maximum characters to return (default 50000)"
                )
            ),
            required = listOf("path")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 30
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val path = parameters["path"]?.toString()
            ?: return ToolResult.error("validation_error", "Parameter 'path' is required")
        val maxChars = (parameters["max_chars"] as? Number)?.toInt()
            ?: DEFAULT_MAX_CHARS
        val pagesArg = parameters["pages"]?.toString()

        val file = File(path)
        if (!file.exists()) {
            return ToolResult.error("file_not_found", "File not found: $path")
        }
        if (!file.canRead()) {
            return ToolResult.error("permission_denied", "Cannot read file: $path")
        }

        PdfToolUtils.initPdfBox(context)

        return try {
            val doc = PDDocument.load(file)
            val result = try {
                val stripper = PDFTextStripper()
                val totalPages = doc.numberOfPages

                if (pagesArg != null) {
                    val range = PdfToolUtils.parsePageRange(pagesArg, totalPages)
                        ?: return ToolResult.error(
                            "invalid_page_range",
                            "Invalid page range: $pagesArg (document has $totalPages pages)"
                        )
                    stripper.startPage = range.first
                    stripper.endPage = range.second
                }

                val text = stripper.getText(doc)

                if (text.isBlank()) {
                    ToolResult.success(
                        "No text content found in PDF. This may be a scanned document. " +
                            "Use pdf_render_page to render pages as images for visual inspection."
                    )
                } else {
                    val truncated = if (text.length > maxChars) {
                        text.take(maxChars) +
                            "\n\n[Truncated at $maxChars characters. " +
                            "Total text length: ${text.length}. " +
                            "Use 'pages' parameter to extract specific pages.]"
                    } else {
                        text
                    }

                    val header = "Extracted text from ${file.name}" +
                        (if (pagesArg != null) " (pages: $pagesArg)" else "") +
                        " [$totalPages total pages]:\n\n"

                    ToolResult.success(header + truncated)
                }
            } finally {
                doc.close()
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract PDF text: $path", e)
            ToolResult.error("pdf_error", "Failed to extract PDF text: ${e.message}")
        }
    }
}
```

### PdfRenderPageTool

```kotlin
/**
 * Located in: tool/builtin/PdfRenderPageTool.kt
 *
 * Renders a PDF page to a PNG image using Android's PdfRenderer.
 * Saves the output to the app's internal pdf-renders/ directory.
 */
class PdfRenderPageTool(
    private val context: Context
) : Tool {

    companion object {
        private const val TAG = "PdfRenderPageTool"
        private const val DEFAULT_DPI = 150
        private const val MIN_DPI = 72
        private const val MAX_DPI = 300
    }

    override val definition = ToolDefinition(
        name = "pdf_render_page",
        description = "Render a PDF page to a PNG image. " +
            "Useful for scanned PDFs or pages with complex layouts, charts, or images. " +
            "The rendered image is saved to pdf-renders/ in the app's storage.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "path" to ToolParameter(
                    type = "string",
                    description = "Path to the PDF file"
                ),
                "page" to ToolParameter(
                    type = "integer",
                    description = "Page number to render (1-based)"
                ),
                "dpi" to ToolParameter(
                    type = "integer",
                    description = "Render resolution in DPI (default 150, min 72, max 300)"
                )
            ),
            required = listOf("path", "page")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 30
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val path = parameters["path"]?.toString()
            ?: return ToolResult.error("validation_error", "Parameter 'path' is required")
        val pageNum = (parameters["page"] as? Number)?.toInt()
            ?: return ToolResult.error("validation_error", "Parameter 'page' is required")
        val dpi = ((parameters["dpi"] as? Number)?.toInt() ?: DEFAULT_DPI)
            .coerceIn(MIN_DPI, MAX_DPI)

        val file = File(path)
        if (!file.exists()) {
            return ToolResult.error("file_not_found", "File not found: $path")
        }
        if (!file.canRead()) {
            return ToolResult.error("permission_denied", "Cannot read file: $path")
        }

        return try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)

            val pageIndex = pageNum - 1
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                renderer.close()
                fd.close()
                return ToolResult.error(
                    "invalid_page",
                    "Page $pageNum out of range (document has ${renderer.pageCount} pages)"
                )
            }

            val page = renderer.openPage(pageIndex)
            val scale = dpi / 72f
            val width = (page.width * scale).toInt()
            val height = (page.height * scale).toInt()

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)

            page.render(
                bitmap,
                null,
                null,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )
            page.close()
            renderer.close()
            fd.close()

            // Save PNG to app's internal storage
            val outputDir = File(context.filesDir, "pdf-renders").also { it.mkdirs() }
            val baseName = file.nameWithoutExtension
            val outputFile = File(outputDir, "${baseName}-page${pageNum}.png")
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()

            ToolResult.success(
                "Page $pageNum rendered and saved to: ${outputFile.absolutePath}\n" +
                    "Resolution: ${width}x${height} (${dpi} DPI)\n" +
                    "File size: ${outputFile.length()} bytes"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render PDF page: $path page $pageNum", e)
            ToolResult.error("pdf_error", "Failed to render PDF page: ${e.message}")
        }
    }
}
```

### PDFBox Android 依赖

在 `app/build.gradle.kts` 中添加：

```kotlin
dependencies {
    // ... existing dependencies ...

    // PDF tools: PDFBox for text extraction and metadata
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
```

PDFBox Android 信息：
- 大小：约 2.5MB（包含字体和资源文件）
- 许可证：Apache 2.0
- 传递依赖：无重要依赖
- 兼容 Android API 21+
- 已在 OneClaw 1.0 的 `lib-pdf` 模块中使用

### ToolModule 变更

```kotlin
// In ToolModule.kt, add imports:
import com.oneclaw.shadow.tool.builtin.PdfInfoTool
import com.oneclaw.shadow.tool.builtin.PdfExtractTextTool
import com.oneclaw.shadow.tool.builtin.PdfRenderPageTool
import com.oneclaw.shadow.tool.util.PdfToolUtils

val toolModule = module {
    // ... existing declarations ...

    // RFC-033: PDF tools
    single {
        PdfToolUtils.initPdfBox(androidContext())
        PdfInfoTool(androidContext())
    }
    single {
        PdfExtractTextTool(androidContext())
    }
    single {
        PdfRenderPageTool(androidContext())
    }

    single {
        ToolRegistry().apply {
            // ... existing tool registrations ...

            // RFC-033: PDF tools
            try {
                register(get<PdfInfoTool>(), ToolSourceInfo.BUILTIN)
            } catch (e: Exception) {
                Log.e("ToolModule", "Failed to register pdf_info: ${e.message}")
            }
            try {
                register(get<PdfExtractTextTool>(), ToolSourceInfo.BUILTIN)
            } catch (e: Exception) {
                Log.e("ToolModule", "Failed to register pdf_extract_text: ${e.message}")
            }
            try {
                register(get<PdfRenderPageTool>(), ToolSourceInfo.BUILTIN)
            } catch (e: Exception) {
                Log.e("ToolModule", "Failed to register pdf_render_page: ${e.message}")
            }

            // ... JS tool loading (unchanged) ...
        }
    }

    // ... rest of module unchanged ...
}
```

## 实现计划

### 阶段 1：依赖与工具类

1. [ ] 在 `app/build.gradle.kts` 中添加 `com.tom-roush:pdfbox-android:2.0.27.0`
2. [ ] 在 `tool/util/` 中创建 `PdfToolUtils.kt`
3. [ ] 创建 `PdfToolUtilsTest.kt`，包含对 `parsePageRange()` 的测试
4. [ ] 验证构建可成功编译

### 阶段 2：PdfInfoTool

1. [ ] 在 `tool/builtin/` 中创建 `PdfInfoTool.kt`
2. [ ] 创建包含单元测试的 `PdfInfoToolTest.kt`
3. [ ] 在 `ToolModule.kt` 中注册
4. [ ] 验证 `./gradlew test` 通过

### 阶段 3：PdfExtractTextTool

1. [ ] 在 `tool/builtin/` 中创建 `PdfExtractTextTool.kt`
2. [ ] 创建包含单元测试的 `PdfExtractTextToolTest.kt`
3. [ ] 在 `ToolModule.kt` 中注册
4. [ ] 验证 `./gradlew test` 通过

### 阶段 4：PdfRenderPageTool

1. [ ] 在 `tool/builtin/` 中创建 `PdfRenderPageTool.kt`
2. [ ] 创建包含单元测试的 `PdfRenderPageToolTest.kt`
3. [ ] 在 `ToolModule.kt` 中注册
4. [ ] 验证 `./gradlew test` 通过

### 阶段 5：集成测试

1. [ ] 运行完整 Layer 1A 测试套件（`./gradlew test`）
2. [ ] 若有模拟器可用，运行 Layer 1B 测试
3. [ ] 在真实设备上使用真实 PDF 文件进行手动测试
4. [ ] 编写测试报告

## 数据模型

无数据模型或数据库变更。工具对文件进行操作，并通过现有 `ToolResult` 类型返回字符串结果。

## API 设计

### 工具接口

```
Tool: pdf_info
Parameters:
  - path: string (required) -- Path to the PDF file
Returns on success:
  Multi-line text with file info, page count, and metadata fields
Returns on error:
  ToolResult.error with error type and message

Tool: pdf_extract_text
Parameters:
  - path: string (required) -- Path to the PDF file
  - pages: string (optional) -- Page range specification
  - max_chars: integer (optional, default: 50000) -- Output character limit
Returns on success:
  Header line + extracted text content
Returns on error:
  ToolResult.error with error type and message

Tool: pdf_render_page
Parameters:
  - path: string (required) -- Path to the PDF file
  - page: integer (required) -- Page number (1-based)
  - dpi: integer (optional, default: 150) -- Render resolution (72-300)
Returns on success:
  Text with output file path, resolution, and file size
Returns on error:
  ToolResult.error with error type and message
```

## 错误处理

| 错误类型 | 原因 | 响应 |
|------------|-------|----------|
| `validation_error` | 缺少或无效的必填参数 | `ToolResult.error("validation_error", "Parameter 'X' is required")` |
| `file_not_found` | 指定路径下文件不存在 | `ToolResult.error("file_not_found", "File not found: <path>")` |
| `permission_denied` | 无法读取文件 | `ToolResult.error("permission_denied", "Cannot read file: <path>")` |
| `invalid_page` | 页码超出文档范围 | `ToolResult.error("invalid_page", "Page N out of range (document has M pages)")` |
| `invalid_page_range` | 页面范围字符串格式错误 | `ToolResult.error("invalid_page_range", "Invalid page range: <spec>")` |
| `pdf_error` | PDFBox 或 PdfRenderer 异常 | `ToolResult.error("pdf_error", "Failed to ...: <exception message>")` |

所有错误均遵循其他内置工具所使用的 `ToolResult.error(errorType, errorMessage)` 模式。

## 安全考量

1. **文件访问**：工具接受文件路径。文件从应用私有存储（context.filesDir）中访问，无需外部存储权限。路径通过 FsBridge 允许列表验证，确保只能访问应用存储范围内的文件。

2. **资源管理**：`PDDocument`、`PdfRenderer`、`ParcelFileDescriptor` 和 `Bitmap` 对象均在 try-finally 块中被正确关闭或回收，以防止资源泄漏。

3. **内存安全**：PDFBox 处理大型 PDF 时可能消耗大量内存。工具不会一次性将整个文档加载到内存中——`PDFTextStripper` 按顺序逐页处理。`PdfRenderer` 每次渲染一页，位图在保存后立即回收。

4. **输出隔离**：渲染生成的 PNG 文件保存到应用内部存储（`context.filesDir/pdf-renders/`），而非共享的外部存储。

5. **无网络访问**：所有操作均为本地文件操作，不向外部发送任何数据。

## 性能

| 操作 | 预期耗时 | 备注 |
|-----------|--------------|-------|
| `pdf_info`（典型 PDF） | < 500ms | 打开文件、读取元数据、关闭 |
| `pdf_extract_text`（10 页） | < 1s | PDFTextStripper 顺序处理 |
| `pdf_extract_text`（100 页） | < 5s | 与页数线性增长 |
| `pdf_render_page`（150 DPI） | < 2s | 渲染 + PNG 压缩 |
| `pdf_render_page`（300 DPI） | < 5s | 像素数为 150 DPI 的 4 倍 |

内存占用：
- PDDocument：典型 PDF 约 2-10MB（使用后关闭）
- 150 DPI 位图（A4 页面）：约 3.5MB（保存后回收）
- 300 DPI 位图（A4 页面）：约 14MB（保存后回收）

## 测试策略

### 单元测试

**PdfToolUtilsTest.kt：**
- `testParsePageRange_singlePage` -- "3" 返回 (3, 3)
- `testParsePageRange_range` -- "1-5" 返回 (1, 5)
- `testParsePageRange_commaSeparated` -- "1,3,5-7" 返回 (1, 7)
- `testParsePageRange_rangeWithSpaces` -- "1 - 5" 返回 (1, 5)
- `testParsePageRange_invalidRange` -- "5-2" 返回 null
- `testParsePageRange_outOfBounds` -- 10 页文档中 "0" 和 "11" 返回 null
- `testParsePageRange_nonNumeric` -- "abc" 返回 null
- `testParsePageRange_singlePageRange` -- "1-1" 返回 (1, 1)

**PdfInfoToolTest.kt：**
- `testDefinition` -- 工具定义包含正确的名称、参数和权限
- `testExecute_missingPath` -- 返回 validation 错误
- `testExecute_fileNotFound` -- 返回 file_not_found 错误
- `testExecute_validPdf` -- 返回页数、文件大小和元数据（使用测试 PDF 资源）

**PdfExtractTextToolTest.kt：**
- `testDefinition` -- 工具定义包含正确的名称、参数和权限
- `testExecute_missingPath` -- 返回 validation 错误
- `testExecute_fileNotFound` -- 返回 file_not_found 错误
- `testExecute_extractAllPages` -- 返回完整文本（使用测试 PDF 资源）
- `testExecute_extractPageRange` -- 返回指定页面的文本
- `testExecute_invalidPageRange` -- 返回 invalid_page_range 错误
- `testExecute_truncation` -- 超出 max_chars 时返回截断文本及提示
- `testExecute_defaultMaxChars` -- 默认 max_chars 为 50000

**PdfRenderPageToolTest.kt：**
- `testDefinition` -- 工具定义包含正确的名称、参数和权限
- `testExecute_missingPath` -- 返回 validation 错误
- `testExecute_missingPage` -- 返回 validation 错误
- `testExecute_fileNotFound` -- 返回 file_not_found 错误
- `testExecute_pageOutOfRange` -- 返回 invalid_page 错误
- `testExecute_dpiClamping` -- 超出 72-300 范围的 DPI 值被限制在边界内

### 测试 PDF 资源

将测试 PDF 文件放置于 `app/src/test/resources/`：
- `test-document.pdf` -- 一个包含已知内容的简单文本 PDF（2-3 页）
- 在测试初始化时通过 PDFBox 以编程方式创建，或作为小型测试固件直接提交

### 集成测试（手动）

1. 在设备上安装应用，并将 PDF 文件放置于 `/sdcard/Documents/`
2. 请求智能体总结一个文本 PDF
3. 请求智能体渲染一个扫描 PDF 的某页
4. 请求智能体从长文档中提取特定页面
5. 验证工具调用在聊天历史中正确显示

## 备选方案考量

### 1. 单一 PdfTool 类，带 mode 参数

**方案**：一个 `PdfTool` 类，带 `mode` 参数（"info"、"extract_text"、"render_page"），类似于 `BrowserTool`。

**否决原因**：三种操作的参数集差异很大。将它们合并为一个带 mode 参数的工具会使参数 schema 复杂且难以理解，不利于 AI 模型的工具调用行为。独立工具配合聚焦的参数 schema 可产生更好的 AI 工具调用效果。

### 2. 基于 JavaScript 的 PDF 工具

**方案**：使用 QuickJS 中的 PDF 库，以 JS 工具形式实现 PDF 工具。

**否决原因**：QuickJS 没有 DOM API，文件 I/O 能力有限。PDF 解析库需要原生代码或完整的 JVM 支持。PDFBox Android 和 PdfRenderer 是 Kotlin/Java 原生库，无法在 QuickJS 中运行。Kotlin 内置工具方案是正确的选择。

### 3. 仅使用 Android PdfRenderer（不使用 PDFBox）

**方案**：使用 Android 内置的 PdfRenderer 处理所有操作，包括通过渲染页面并进行 OCR 来提取文本。

**否决原因**：PdfRenderer 只能将页面渲染为位图，无法直接提取文本。使用 OCR 进行文本提取会速度慢、准确率低，并带来不必要的复杂性。PDFBox 通过 PDFTextStripper 提供直接文本提取能力。

## 依赖项

### 外部依赖

| 依赖项 | 版本 | 大小 | 许可证 |
|------------|---------|------|---------|
| com.tom-roush:pdfbox-android | 2.0.27.0 | ~2.5MB | Apache 2.0 |
| Android PdfRenderer | 内置（API 21+） | 0 | Android Framework |

### 内部依赖

- `tool/engine/` 中的 `Tool` 接口
- `core/model/` 中的 `ToolResult`、`ToolDefinition`、`ToolParametersSchema`、`ToolParameter`
- `tool/engine/` 和 `core/model/` 中的 `ToolRegistry`、`ToolSourceInfo`
- Android 框架中的 `Context`（通过 Koin 的 `androidContext()` 注入）

## 未来扩展

- **密码保护 PDF**：向 `pdf_extract_text` 和 `pdf_info` 添加可选的 `password` 参数
- **批量页面渲染**：向 `pdf_render_page` 添加 `pages` 参数，支持一次渲染多页
- **PDF 搜索**：添加 `pdf_search` 工具，在 PDF 中搜索文本并返回页码及上下文
- **PDF 表格提取**：利用 PDFBox 的表格检测启发式算法实现专门的表格提取
- **PDF 书签/大纲提取**：提取文档大纲或目录

## 变更历史

| 日期 | 版本 | 变更内容 | 负责人 |
|------|---------|---------|-------|
| 2026-03-01 | 0.1 | 初始版本 | - |
