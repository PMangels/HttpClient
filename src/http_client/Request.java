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
        //TODO: post and put requests should be able to send data to the server. Also add headers
        this.type = type;
        if (!path.startsWith("/"))
            path = "/" + path;

        this.path = path;
    }

    public String requestString() {
        return getType().getTypeString() + " " + getPath() + " " + "HTTP/1.1\r\n";
    }
}
