package http_client;

import http_datastructures.Response;

public class ConnectionResponse {
    public final Response response;
    public final Connection connection;

    public ConnectionResponse(Response response, Connection connection) {
        this.response = response;
        this.connection = connection;
    }
}
