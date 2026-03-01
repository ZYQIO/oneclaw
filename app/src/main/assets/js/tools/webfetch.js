async function execute(params) {
    var url = params.url;
    if (!url) return { error: "Parameter 'url' is required" };

    var response = await fetch(url);

    if (!response.ok) {
        return {
            error: "HTTP " + response.status + ": " + response.statusText,
            url: url
        };
    }

    var body = await response.text();
    var contentType = (response.headers["content-type"] || "").toLowerCase();

    // If not HTML, return raw body
    if (contentType.indexOf("text/html") === -1) {
        return body;
    }

    // Strip non-content elements before Turndown conversion
    var cleaned = body
        .replace(/<script[^>]*>[\s\S]*?<\/script>/gi, "")
        .replace(/<style[^>]*>[\s\S]*?<\/style>/gi, "")
        .replace(/<nav[^>]*>[\s\S]*?<\/nav>/gi, "")
        .replace(/<header[^>]*>[\s\S]*?<\/header>/gi, "")
        .replace(/<footer[^>]*>[\s\S]*?<\/footer>/gi, "")
        .replace(/<noscript[^>]*>[\s\S]*?<\/noscript>/gi, "");

    // Convert HTML to Markdown using Turndown
    var TurndownService = lib("turndown");
    var td = new TurndownService({
        headingStyle: "atx",
        codeBlockStyle: "fenced",
        bulletListMarker: "-"
    });

    var markdown = td.turndown(cleaned);
    return markdown;
}
