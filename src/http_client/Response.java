package http_client;


import java.util.HashMap;
import java.util.Map;

public class Response {
    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getContent() {
        return content;
    }

    private final Map<String, String> headers = new HashMap<>();

    public void setContent(String content) {
        this.content = content;
    }

    private String content;
    private final int statusCode;

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatus() {
        return status;
    }

    public HTTPVersion getVersion() {
        return version;
    }

    private final String status;
    private final HTTPVersion version;

    public Response(String rawResponse) throws UnsupportedHTTPVersionException {
        String[] parts = rawResponse.split("\r\n\r\n", 2);

        String[] headerParts = parts[0].split("\r\n", 2);
        String[] firstLineParts = headerParts[0].split(" ", 3);
        switch (firstLineParts[0]) {
            case "HTTP/1.0":
                this.version = HTTPVersion.HTTP10;
                break;
            case "HTTP/1.1":
                this.version = HTTPVersion.HTTP11;
                break;
            default:
                throw new UnsupportedHTTPVersionException();
        }

        this.statusCode = Integer.parseInt(firstLineParts[1]);
        this.status = firstLineParts[2];

        for (String line: headerParts[1].split("\r\n")) {
            String[] lineParts = line.split(":", 2);
            headers.put(lineParts[0], lineParts[1]);
        }

        this.content = parts[1];
    }

}
