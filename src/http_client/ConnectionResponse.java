package http_client;

public class ConnectionResponse {
    public final Response response;
    public final Connection connection;

    public ConnectionResponse(Response response, Connection connection) {
        this.response = response;
        this.connection = connection;
    }
}
