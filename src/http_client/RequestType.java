package http_client;

public enum RequestType{
    GET("GET"), POST("POST"), HEAD("HEAD"), PUT("PUT");

    private String typeString;

    RequestType(String typeString) {
        this.typeString = typeString;
    }

    public String getTypeString() {
        return typeString;
    }
}
