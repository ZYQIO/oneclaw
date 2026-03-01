/**
 * Located in: assets/js/browser/extract.js
 *
 * Built-in content extraction script that runs in the WebView context.
 * Uses Turndown (injected beforehand) for HTML-to-Markdown conversion.
 * Falls back to innerText extraction if Turndown is unavailable.
 */
(function() {
    // Noise selectors to remove
    var noiseSelectors = [
        'script', 'style', 'noscript', 'svg', 'iframe',
        'nav', 'header', 'footer', 'aside',
        'form', 'button', 'input', 'select', 'textarea',
        '[role="navigation"]', '[role="banner"]', '[role="contentinfo"]',
        '.cookie-banner', '.popup', '.modal', '.advertisement', '.ad'
    ];

    // Clone the document to avoid modifying the actual page
    var clone = document.documentElement.cloneNode(true);

    // Remove noise elements
    noiseSelectors.forEach(function(selector) {
        try {
            var elements = clone.querySelectorAll(selector);
            elements.forEach(function(el) { el.remove(); });
        } catch(e) { /* ignore invalid selectors */ }
    });

    // Find main content area
    var content = clone.querySelector('article')
        || clone.querySelector('main')
        || clone.querySelector('[role="main"]')
        || clone.querySelector('body')
        || clone;

    // Get the title
    var title = document.title || '';

    // Try Turndown if available
    var markdown = '';
    if (typeof TurndownService !== 'undefined') {
        try {
            var td = new TurndownService({
                headingStyle: 'atx',
                codeBlockStyle: 'fenced',
                bulletListMarker: '-'
            });
            // Remove empty links and images without src
            td.addRule('removeEmptyLinks', {
                filter: function(node) {
                    return node.nodeName === 'A' && !node.textContent.trim();
                },
                replacement: function() { return ''; }
            });
            markdown = td.turndown(content.innerHTML);
        } catch(e) {
            // Fall back to innerText
            markdown = content.innerText || content.textContent || '';
        }
    } else {
        // No Turndown available, use innerText
        markdown = content.innerText || content.textContent || '';
    }

    // Prepend title if not already in content
    if (title && markdown.indexOf(title) === -1) {
        markdown = '# ' + title + '\n\n' + markdown;
    }

    // Clean up: collapse multiple blank lines
    markdown = markdown.replace(/\n{3,}/g, '\n\n').trim();

    return markdown;
})();
