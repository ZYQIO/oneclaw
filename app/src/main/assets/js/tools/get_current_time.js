function execute(params) {
    var timezone = params.timezone || "";
    var format = params.format || "iso8601";
    return _time(timezone, format);
}
