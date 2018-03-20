package http_client;

import http_datastructures.*;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.List;
import static java.util.Base64.*;

public class Connection {
    private Socket socket;
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

    public Response sendRequest(Request request) throws IOException, UnsupportedHTTPVersionException, IllegalHeaderException, IllegalResponseException {
        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
        DataInputStream inputStream = new DataInputStream(socket.getInputStream());
        request.addHeader("Host", this.host + ":" + this.port);
        outputStream.writeBytes(request.toString());

        StringBuilder responseBuffer = new StringBuilder();

        try{
            responseBuffer.append((char) inputStream.readByte());
        } catch (EOFException | SocketException e){
            this.socket = new Socket(this.host, this.port);
            outputStream = new DataOutputStream(socket.getOutputStream());
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream.writeBytes(request.toString());
        }

        while (!responseBuffer.toString().endsWith("\r\n\r\n")) {
            responseBuffer.append((char) inputStream.readByte());
        }

        int length = 0;
        HttpContentType type = HttpContentType.UNDEFINED;
        for (String line : responseBuffer.toString().split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                String[] lineParts = line.split(":");
                if (lineParts.length != 2)
                    throw new IllegalHeaderException(line);
                try {
                    length = Integer.parseInt(lineParts[1].trim());
                }catch (NumberFormatException e){
                    throw new IllegalHeaderException(line);
                }
            }
            if (line.toLowerCase().startsWith("content-type:")){
                if(line.toLowerCase().contains("image")){
                    type = HttpContentType.IMAGE;
                }
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
            String[] filenameSplit = request.getPath().split("\\.");
            extension = filenameSplit[filenameSplit.length - 1].toLowerCase();
        } catch (IndexOutOfBoundsException e) {
            extension = "";
        }
        if (imageExtensions.contains(extension)||type.equals(HttpContentType.IMAGE)){
            byteString = new String(getEncoder().encode(bytes),"UTF-8");
        }
        else{
            byteString = new String(bytes, "UTF-8");
        }
        responseBuffer.append(byteString);


        return new Response(responseBuffer.toString());
    }

}
