package http_client;

public class Request {

    private final RequestType type;
    private final String path;

    public RequestType getType() {
        return type;
    }

    public String getPath() {
        return path;
    }

    public Request(RequestType type, String path) {
        this.type = type;
        if (path.isEmpty())
            path = "/";

        this.path = path;
    }

    public String requestString() {
        return getType().getTypeString() + " " + getPath() + " " + "HTTP/1.1\n";
    }
}
