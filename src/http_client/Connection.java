package http_client;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.List;
import static java.util.Base64.*;

public class Connection {
    private final Socket socket;
    private final String host;
    private final int port;

    public String getHost() {
        return host;
    }

    public Connection(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        this.socket = new Socket(host, port);
    }

    public ConnectionResponse sendRequest(Request request) throws IOException, UnsupportedHTTPVersionException {
        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
        DataInputStream inputStream = new DataInputStream(socket.getInputStream());
        outputStream.writeBytes(request.requestString() + "Host: " + this.host + ":" + this.port + "\r\n\r\n");

        StringBuilder responseBuffer = new StringBuilder();

        try{
            while (true) {
                responseBuffer.append((char) inputStream.readByte());
                if (responseBuffer.toString().endsWith("\r\n\r\n")) {
                    break;
                }
            }
        } catch (java.io.EOFException e){
            System.out.println("Socket died, reconnecting.");
            Connection new_connection = new Connection(host, port);
            return new_connection.sendRequest(request);
        }

        int length = 0;
        for (String line : responseBuffer.toString().split("\r\n")) {
            if (line.startsWith("Content-Length: ")) {
                length = Integer.parseInt(line.substring(16));
                break;
            }
        }



        int byteCount = 0;
        byte[] bytes = new byte[length];
        while (byteCount != length) {
            byteCount += inputStream.read(bytes, byteCount, length - byteCount);
        }

        List<String> imageExtensions = Arrays.asList("jpeg", "jpg", "png", "bmp", "wbmp", "gif");
        String byteString;
        String extension;
        try {
            String[] urlSplit = request.getPath().split("/");
            String filename = urlSplit[urlSplit.length - 1];
            String[] filenameSplit = filename.split("\\.");
            extension = filenameSplit[filenameSplit.length - 1].toLowerCase();
        } catch (IndexOutOfBoundsException e) {
            extension = "";
        }
        if (imageExtensions.contains(extension)){
            byteString = new String(getEncoder().encode(bytes),"UTF-8");
        }
        else{
            byteString = new String(bytes, "UTF-8");
        }
        responseBuffer.append(byteString);


        return new ConnectionResponse(new Response(responseBuffer.toString()),this);
    }

}
