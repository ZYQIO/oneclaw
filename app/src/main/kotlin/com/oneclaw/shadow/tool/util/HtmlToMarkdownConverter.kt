package com.oneclaw.shadow.tool.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * Located in: tool/util/HtmlToMarkdownConverter.kt
 *
 * Converts HTML to Markdown using Jsoup DOM traversal.
 * Handles content extraction (article/main detection),
 * noise removal, and element-by-element Markdown rendering.
 */
object HtmlToMarkdownConverter {

    // Elements to remove entirely (content and tag)
    private val NOISE_TAGS = setOf(
        "script", "style", "nav", "header", "footer", "aside",
        "noscript", "svg", "iframe", "form", "button", "input",
        "select", "textarea"
    )

    // Block elements that produce paragraph breaks
    private val BLOCK_TAGS = setOf(
        "p", "div", "section", "article", "main", "figure",
        "figcaption", "details", "summary", "address"
    )

    /**
     * Convert HTML string to Markdown.
     *
     * @param html The raw HTML string
     * @param baseUrl Optional base URL for resolving relative links
     * @return Markdown string
     */
    fun convert(html: String, baseUrl: String? = null): String {
        val doc = if (baseUrl != null) {
            Jsoup.parse(html, baseUrl)
        } else {
            Jsoup.parse(html)
        }

        // Extract title
        val title = doc.title().takeIf { it.isNotBlank() }

        // Remove noise elements
        NOISE_TAGS.forEach { tag ->
            doc.select(tag).remove()
        }

        // Find main content area
        val contentElement = findMainContent(doc)

        // Convert to Markdown
        val markdown = convertElement(contentElement, depth = 0)

        // Clean up whitespace
        val cleaned = cleanupWhitespace(markdown)

        // Prepend title if not already present in content
        return if (title != null && !cleaned.startsWith("# ") && title !in cleaned.take(200)) {
            "# $title\n\n$cleaned"
        } else {
            cleaned
        }
    }

    /**
     * Find the main content element using a priority-based strategy.
     * article > main > [role="main"] > body
     */
    private fun findMainContent(doc: Document): Element {
        // Try <article> first -- most specific content marker
        doc.selectFirst("article")?.let { return it }

        // Try <main>
        doc.selectFirst("main")?.let { return it }

        // Try role="main"
        doc.selectFirst("[role=main]")?.let { return it }

        // Fallback to <body>
        return doc.body() ?: doc
    }

    /**
     * Recursively convert a Jsoup Element to Markdown.
     */
    private fun convertElement(element: Element, depth: Int): String {
        val sb = StringBuilder()

        for (node in element.childNodes()) {
            when (node) {
                is TextNode -> {
                    val text = node.text()
                    if (text.isNotBlank()) {
                        sb.append(text)
                    } else if (text.isNotEmpty() && sb.isNotEmpty() && !sb.endsWith(" ")) {
                        sb.append(" ")
                    }
                }
                is Element -> {
                    sb.append(convertTag(node, depth))
                }
            }
        }

        return sb.toString()
    }

    /**
     * Convert a specific HTML tag to its Markdown equivalent.
     */
    private fun convertTag(el: Element, depth: Int): String {
        val tag = el.tagName().lowercase()

        return when (tag) {
            // Headings
            "h1" -> "\n\n# ${inlineText(el)}\n\n"
            "h2" -> "\n\n## ${inlineText(el)}\n\n"
            "h3" -> "\n\n### ${inlineText(el)}\n\n"
            "h4" -> "\n\n#### ${inlineText(el)}\n\n"
            "h5" -> "\n\n##### ${inlineText(el)}\n\n"
            "h6" -> "\n\n###### ${inlineText(el)}\n\n"

            // Paragraphs and block elements
            "p" -> "\n\n${convertElement(el, depth)}\n\n"
            "div", "section", "article", "main" -> "\n\n${convertElement(el, depth)}\n\n"

            // Links
            "a" -> {
                val href = el.absUrl("href").ifEmpty { el.attr("href") }
                val text = inlineText(el)
                if (text.isNotBlank() && href.isNotBlank()) {
                    "[$text]($href)"
                } else if (text.isNotBlank()) {
                    text
                } else {
                    ""
                }
            }

            // Emphasis
            "strong", "b" -> "**${inlineText(el)}**"
            "em", "i" -> "*${inlineText(el)}*"
            "del", "s", "strike" -> "~~${inlineText(el)}~~"

            // Code
            "code" -> {
                if (el.parent()?.tagName() == "pre") {
                    // Handled by <pre> case
                    el.wholeText()
                } else {
                    "`${el.text()}`"
                }
            }
            "pre" -> {
                val codeEl = el.selectFirst("code")
                val code = codeEl?.wholeText() ?: el.wholeText()
                val lang = codeEl?.className()
                    ?.replace("language-", "")
                    ?.replace("lang-", "")
                    ?.takeIf { it.isNotBlank() && !it.contains(" ") }
                    ?: ""
                "\n\n```$lang\n${code.trimEnd()}\n```\n\n"
            }

            // Lists
            "ul" -> "\n\n${convertList(el, ordered = false, indent = depth)}\n\n"
            "ol" -> "\n\n${convertList(el, ordered = true, indent = depth)}\n\n"

            // Blockquote
            "blockquote" -> {
                val content = convertElement(el, depth).trim()
                val quoted = content.lines().joinToString("\n") { "> $it" }
                "\n\n$quoted\n\n"
            }

            // Images
            "img" -> {
                val alt = el.attr("alt")
                val src = el.absUrl("src").ifEmpty { el.attr("src") }
                if (src.isNotBlank()) "![${alt}]($src)" else ""
            }

            // Horizontal rule
            "hr" -> "\n\n---\n\n"

            // Line break
            "br" -> "\n"

            // Tables
            "table" -> "\n\n${convertTable(el)}\n\n"

            // Definition lists
            "dl" -> "\n\n${convertDefinitionList(el)}\n\n"

            // Figure
            "figure" -> "\n\n${convertElement(el, depth)}\n\n"
            "figcaption" -> "\n*${inlineText(el)}*\n"

            // Other block elements
            in BLOCK_TAGS -> "\n\n${convertElement(el, depth)}\n\n"

            // Unknown/inline elements -- recurse into children
            else -> convertElement(el, depth)
        }
    }

    /**
     * Convert a <ul> or <ol> to Markdown list items with proper nesting.
     */
    private fun convertList(
        listEl: Element,
        ordered: Boolean,
        indent: Int
    ): String {
        val sb = StringBuilder()
        val prefix = "  ".repeat(indent)
        var index = 1

        for (li in listEl.children()) {
            if (li.tagName().lowercase() != "li") continue

            val bullet = if (ordered) "${index}. " else "- "
            val content = StringBuilder()

            for (child in li.childNodes()) {
                when (child) {
                    is TextNode -> {
                        val text = child.text().trim()
                        if (text.isNotBlank()) content.append(text)
                    }
                    is Element -> {
                        if (child.tagName().lowercase() in listOf("ul", "ol")) {
                            // Nested list
                            content.append("\n")
                            content.append(convertList(
                                child,
                                ordered = child.tagName().lowercase() == "ol",
                                indent = indent + 1
                            ))
                        } else {
                            content.append(inlineText(child))
                        }
                    }
                }
            }

            sb.append("$prefix$bullet${content.toString().trim()}\n")
            index++
        }

        return sb.toString().trimEnd()
    }

    /**
     * Convert a <table> to Markdown table format.
     */
    private fun convertTable(table: Element): String {
        val rows = mutableListOf<List<String>>()

        // Collect all rows from thead, tbody, tfoot
        for (row in table.select("tr")) {
            val cells = row.select("th, td").map { inlineText(it).trim() }
            if (cells.isNotEmpty()) {
                rows.add(cells)
            }
        }

        if (rows.isEmpty()) return ""

        // Determine column count
        val colCount = rows.maxOf { it.size }

        // Pad rows to equal column count
        val paddedRows = rows.map { row ->
            row + List(colCount - row.size) { "" }
        }

        val sb = StringBuilder()

        // Header row
        sb.append("| ${paddedRows[0].joinToString(" | ")} |\n")
        // Separator
        sb.append("| ${paddedRows[0].map { "---" }.joinToString(" | ")} |\n")
        // Data rows
        for (i in 1 until paddedRows.size) {
            sb.append("| ${paddedRows[i].joinToString(" | ")} |\n")
        }

        return sb.toString().trimEnd()
    }

    /**
     * Convert a <dl> to Markdown format.
     */
    private fun convertDefinitionList(dl: Element): String {
        val sb = StringBuilder()
        for (child in dl.children()) {
            when (child.tagName().lowercase()) {
                "dt" -> sb.append("**${inlineText(child)}**\n")
                "dd" -> sb.append(": ${inlineText(child)}\n\n")
            }
        }
        return sb.toString().trimEnd()
    }

    /**
     * Extract inline text from an element, stripping all HTML tags.
     * Preserves inline Markdown formatting from child elements.
     */
    private fun inlineText(el: Element): String {
        val sb = StringBuilder()
        for (node in el.childNodes()) {
            when (node) {
                is TextNode -> sb.append(node.text())
                is Element -> {
                    when (node.tagName().lowercase()) {
                        "strong", "b" -> sb.append("**${inlineText(node)}**")
                        "em", "i" -> sb.append("*${inlineText(node)}*")
                        "code" -> sb.append("`${node.text()}`")
                        "a" -> {
                            val href = node.absUrl("href").ifEmpty { node.attr("href") }
                            val text = inlineText(node)
                            if (text.isNotBlank() && href.isNotBlank()) {
                                sb.append("[$text]($href)")
                            } else {
                                sb.append(text)
                            }
                        }
                        "br" -> sb.append("\n")
                        "img" -> {
                            val alt = node.attr("alt")
                            val src = node.absUrl("src").ifEmpty { node.attr("src") }
                            if (src.isNotBlank()) sb.append("![${alt}]($src)")
                        }
                        else -> sb.append(inlineText(node))
                    }
                }
            }
        }
        return sb.toString()
    }

    /**
     * Clean up whitespace in the final Markdown output.
     */
    private fun cleanupWhitespace(markdown: String): String {
        return markdown
            .replace(Regex("[ \t]+\n"), "\n")  // Trailing whitespace
            .replace(Regex("\n[ \t]+\n"), "\n\n")  // Lines with only whitespace
            .replace(Regex("\n{3,}"), "\n\n")  // Collapse multiple blank lines (run last)
            .trim()
    }
}
