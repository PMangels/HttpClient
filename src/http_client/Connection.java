package http_client;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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

    public Response sendRequest(Request request) throws IOException {
        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
        BufferedReader inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        outputStream.writeBytes(request.requestString() + "Host: " + this.host + ":" + this.port + "\n\n");
        StringBuffer responseBuffer = new StringBuffer();
        String line = inputStream.readLine();
        responseBuffer.append(line + "\n");
        String lastLine = "";
        int length = 0;
        while (!(line.isEmpty() && lastLine.isEmpty())) {
            line = inputStream.readLine();
            responseBuffer.append(line + "\n");
            if (line.startsWith("Content-Length:"))
                length = Integer.parseInt(line.substring(16));
            lastLine = line;
        }
        int bytesRead = 0;
        while (bytesRead != length){
            line = inputStream.readLine();
            responseBuffer.append(line + "\n");
            bytesRead += line.length() + 1;
        }

        return new Response(responseBuffer.toString());
    }

}
