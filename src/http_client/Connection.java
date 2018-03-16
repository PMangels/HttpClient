package http_client;

import java.io.*;
import java.net.Socket;

public class Connection {
    private final Socket socket;
    private final String host;
    private final int port;

    public Connection(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        this.socket = new Socket(host, port);
    }

    public Response sendRequest(Request request) throws IOException, UnsupportedHTTPVersionException {
        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
        DataInputStream inputStream = new DataInputStream(socket.getInputStream());
        outputStream.writeBytes(request.requestString() + "Host: " + this.host + ":" + this.port + "\r\n\r\n");

        StringBuilder responseBuffer = new StringBuilder();

        while (true) {
            responseBuffer.append((char)inputStream.readByte());
            if (responseBuffer.toString().endsWith("\r\n\r\n")){
                break;
            }
        }

        int length = 0;
        for(String line: responseBuffer.toString().split("\r\n")) {
            if (line.startsWith("Content-Length: ")){
                length = Integer.parseInt(line.substring(16));
                break;
            }
        }
        int byteCount = 0;
        byte[] bytes = new byte[length];
        while (byteCount != length) {
            byteCount += inputStream.read(bytes, byteCount, length-byteCount);
        }
        responseBuffer.append(new String(bytes, "UTF-8"));


        return new Response(responseBuffer.toString());
    }

}
