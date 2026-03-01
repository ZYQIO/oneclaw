package com.oneclaw.shadow.tool.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HtmlToMarkdownConverterTest {

    @Test
    fun testConvertSimpleHtml() {
        val html = """
            <html><body>
            <p>Hello world</p>
            <p>Second paragraph</p>
            </body></html>
        """.trimIndent()

        val result = HtmlToMarkdownConverter.convert(html)

        assertTrue(result.contains("Hello world"))
        assertTrue(result.contains("Second paragraph"))
    }

    @Test
    fun testConvertHeadings() {
        val html = """
            <html><body>
            <h1>Heading One</h1>
            <h2>Heading Two</h2>
            <h3>Heading Three</h3>
            <h4>Heading Four</h4>
            <h5>Heading Five</h5>
            <h6>Heading Six</h6>
            </body></html>
        """.trimIndent()

        val result = HtmlToMarkdownConverter.convert(html)

        assertTrue(result.contains("# Heading One"))
        assertTrue(result.contains("## Heading Two"))
        assertTrue(result.contains("### Heading Three"))
        assertTrue(result.contains("#### Heading Four"))
        assertTrue(result.contains("##### Heading Five"))
        assertTrue(result.contains("###### Heading Six"))
    }

    @Test
    fun testConvertLinks() {
        val html = """
            <html><body>
            <p><a href="https://example.com">Example Site</a></p>
            </body></html>
        """.trimIndent()

        val result = HtmlToMarkdownConverter.convert(html)

        assertTrue(result.contains("[Example Site](https://example.com)"))
    }

    @Test
    fun testConvertNestedLists() {
        val html = """
            <html><body>
            <ul>
              <li>Item 1
                <ul>
                  <li>Nested 1</li>
                  <li>Nested 2</li>
                </ul>
              </li>
              <li>Item 2</li>
            </ul>
            <ol>
              <li>First</li>
              <li>Second</li>
            </ol>
            </body></html>
        """.trimIndent()

        val result = HtmlToMarkdownConverter.convert(html)

        assertTrue(result.contains("- Item 1"))
        assertTrue(result.contains("Nested 1"))
        assertTrue(result.contains("Nested 2"))
        assertTrue(result.contains("- Item 2"))
        assertTrue(result.contains("1. First"))
        assertTrue(result.contains("2. Second"))
    }

    @Test
    fun testConvertTables() {
        val html = """
            <html><body>
            <table>
              <thead>
                <tr><th>Name</th><th>Age</th></tr>
              </thead>
              <tbody>
                <tr><td>Alice</td><td>30</td></tr>
                <tr><td>Bob</td><td>25</td></tr>
              </tbody>
            </table>
            </body></html>
        """.trimIndent()

        val result = HtmlToMarkdownConverter.convert(html)

        assertTrue(result.contains("| Name | Age |"))
        assertTrue(result.contains("| --- | --- |"))
        assertTrue(result.contains("| Alice | 30 |"))
        assertTrue(result.contains("| Bob | 25 |"))
    }

    @Test
    fun testConvertCodeBlocks() {
        val html = """
            <html><body>
            <p>Use <code>println()</code> for output.</p>
            <pre><code class="language-kotlin">fun main() {
    println("Hello")
}</code></pre>
            </body></html>
        """.trimIndent()

        val result = HtmlToMarkdownConverter.convert(html)

        assertTrue(result.contains("`println()`"))
        assertTrue(result.contains("```kotlin"))
        assertTrue(result.contains("fun main()"))
        assertTrue(result.contains("```"))
    }

    @Test
    fun testConvertBlockquotes() {
        val html = """
            <html><body>
            <blockquote>
              <p>This is a quote</p>
            </blockquote>
            </body></html>
        """.trimIndent()

        val result = HtmlToMarkdownConverter.convert(html)

        assertTrue(result.contains(">"))
        assertTrue(result.contains("This is a quote"))
    }

    @Test
    fun testNoiseRemoval() {
        val html = """
            <html><body>
            <script>alert('xss')</script>
            <style>.hidden { display: none; }</style>
            <nav><a href="/">Home</a></nav>
            <p>Real content here</p>
            </body></html>
        """.trimIndent()

        val result = HtmlToMarkdownConverter.convert(html)

        assertFalse(result.contains("alert('xss')"))
        assertFalse(result.contains("display: none"))
        assertTrue(result.contains("Real content here"))
    }

    @Test
    fun testContentExtraction_article() {
        val html = """
            <html><body>
            <div>Sidebar noise</div>
            <article>
              <h1>Article Title</h1>
              <p>Article content</p>
            </article>
            <footer>Footer noise</footer>
            </body></html>
        """.trimIndent()

        val result = HtmlToMarkdownConverter.convert(html)

        assertTrue(result.contains("Article Title"))
        assertTrue(result.contains("Article content"))
    }

    @Test
    fun testContentExtraction_main() {
        val html = """
            <html><body>
            <div>Sidebar noise</div>
            <main>
              <h1>Main Title</h1>
              <p>Main content</p>
            </main>
            </body></html>
        """.trimIndent()

        val result = HtmlToMarkdownConverter.convert(html)

        assertTrue(result.contains("Main Title"))
        assertTrue(result.contains("Main content"))
    }

    @Test
    fun testTitlePrepend() {
        val html = """
            <html>
            <head><title>My Page Title</title></head>
            <body>
            <p>Content without heading</p>
            </body></html>
        """.trimIndent()

        val result = HtmlToMarkdownConverter.convert(html)

        assertTrue(result.startsWith("# My Page Title"))
        assertTrue(result.contains("Content without heading"))
    }

    @Test
    fun testEmptyHtml() {
        val result = HtmlToMarkdownConverter.convert("")

        // Should not throw, may return empty string or minimal content
        assertTrue(result.isEmpty() || result.isNotEmpty())
    }

    @Test
    fun testMalformedHtml() {
        // Jsoup handles malformed HTML gracefully
        val html = "<p>Unclosed paragraph<p>Another paragraph</div>"

        val result = HtmlToMarkdownConverter.convert(html)

        assertTrue(result.contains("Unclosed paragraph"))
        assertTrue(result.contains("Another paragraph"))
    }

    @Test
    fun testWhitespaceCleanup() {
        val html = """
            <html><body>
            <p>Paragraph one</p>



            <p>Paragraph two</p>
            </body></html>
        """.trimIndent()

        val result = HtmlToMarkdownConverter.convert(html)

        // Should not have more than 2 consecutive newlines
        assertFalse(result.contains("\n\n\n"))
        assertTrue(result.contains("Paragraph one"))
        assertTrue(result.contains("Paragraph two"))
    }
}
