function execute(params) {
    var path = params.path;
    if (!path) return { error: "Parameter 'path' is required" };
    return fs.readFile(path);
}
