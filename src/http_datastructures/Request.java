package http_datastructures;

public class Request extends HTTPMessage {

    private final RequestType type;
    private final String path;

    public RequestType getType() {
        return type;
    }

    public String getPath() {
        return path;
    }

    public Request(RequestType type, String path, HTTPVersion version, String content) {
        super(version, content);

        this.type = type;
        if (!path.startsWith("/"))
            path = "/" + path;

        this.path = path;
    }

    public Request(String requestString) throws UnsupportedHTTPCommandException, UnsupportedHTTPVersionException {
        super(requestString);
        String[] firstLine = this.firstLine.split(" ");
        try {
            this.type = RequestType.valueOf(firstLine[0]);
        }catch (IllegalArgumentException e){
            throw new UnsupportedHTTPCommandException();
        }

        this.path = firstLine[1];

        try {
            this.version = HTTPVersion.valueOf(firstLine[2]);
        }catch (IllegalArgumentException e){
            throw new UnsupportedHTTPVersionException();
        }

    }

    @Override
    public String toString() {
        this.firstLine = this.type.typeString + " " + this.path + " " + this.getVersion().versionString;
        return super.toString();
    }
}
