async function execute(params) {
    var url = params.url;
    if (!url) return { error: "Parameter 'url' is required" };

    var method = (params.method || "GET").toUpperCase();
    var options = { method: method };

    if (params.headers) {
        options.headers = params.headers;
    }
    if (params.body && (method === "POST" || method === "PUT")) {
        options.body = params.body;
    }

    var response = await fetch(url, options);
    var body = await response.text();

    var result = "HTTP " + response.status + " " + response.statusText + "\n";

    if (response.headers["content-type"]) {
        result += "Content-Type: " + response.headers["content-type"] + "\n";
    }
    if (response.headers["content-length"]) {
        result += "Content-Length: " + response.headers["content-length"] + "\n";
    }

    result += "\n" + body;
    return result;
}
