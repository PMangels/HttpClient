package http_datastructures;

public class Response extends HTTPMessage {

    private final int statusCode;
    private final String status;

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatus() {
        return status;
    }

    public Response(String rawResponse) throws UnsupportedHTTPVersionException {
        super(rawResponse);

        String[] firstLine = this.firstLine.split(" ", 3);

        try {
            this.version = HTTPVersion.valueOf(firstLine[0]);
        }catch (IllegalArgumentException e){
            throw new UnsupportedHTTPVersionException();
        }

        this.statusCode = Integer.parseInt(firstLine[1]);
        this.status = firstLine[2];
    }

    public Response(HTTPVersion version, int statusCode, String status, String content){
        super(version, content);

        this.statusCode = statusCode;
        this.status = status;
    }

    @Override
    public String toString() {
        this.firstLine = this.version.versionString + " " + this.statusCode + " " + this.status;
        return super.toString();
    }
}
