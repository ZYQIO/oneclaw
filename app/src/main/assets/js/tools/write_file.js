function execute(params) {
    var path = params.path;
    var content = params.content;
    var mode = params.mode || "overwrite";

    if (!path) return { error: "Parameter 'path' is required" };
    if (content === undefined || content === null) return { error: "Parameter 'content' is required" };

    if (mode === "append") {
        fs.appendFile(path, content);
    } else {
        fs.writeFile(path, content);
    }

    var bytes = content.length;
    return "Successfully wrote " + bytes + " bytes to " + path + " (mode: " + mode + ")";
}
