package http_client;

import http_datastructures.*;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

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
        do {
            int index = responseBuffer.indexOf("\r\n\r\n");
            if (index != -1)
                responseBuffer.delete(0, index);
            while (!responseBuffer.toString().endsWith("\r\n\r\n")) {
                responseBuffer.append((char) inputStream.readByte());
            }
        } while (responseBuffer.toString().endsWith("100 Continue\r\n\r\n"));

        if (request.getType() == RequestType.HEAD)
            return new Response(responseBuffer.toString());

        int length = 0;
        boolean isImage = false;
        boolean chunked = false;
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
                    isImage = true;
                }
            }
            if (line.toLowerCase().startsWith("transfer-encoding:") && line.toLowerCase().contains("chunked")){
                chunked = true;
            }
        }

        byte[] bytes;
        if (chunked){
            bytes = parseBodyChunked(inputStream);
            do{
                responseBuffer.insert(responseBuffer.length()-2,(char) inputStream.readByte());
            }while (!responseBuffer.toString().endsWith("\r\n\r\n\r\n"));
            responseBuffer.delete(responseBuffer.length()-2,responseBuffer.length());
        }else{
            bytes = parseBody(inputStream, length);
        }

        String byteString;
        if (isImage){
            byteString = new String(getEncoder().encode(bytes),"UTF-8");
        }
        else{
            byteString = new String(bytes, "UTF-8");
        }
        responseBuffer.append(byteString);

        return new Response(responseBuffer.toString());
    }

    private byte[] parseBodyChunked(DataInputStream inputStream) throws IOException {
        int length = -1;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (length != 0){
            StringBuilder responseBuffer = new StringBuilder();
            while (!responseBuffer.toString().endsWith("\r\n")) {
                responseBuffer.append((char) inputStream.readByte());
            }
            String[] firstline = responseBuffer.toString().split(";");
            length = Integer.parseInt(firstline[0].replace("\r\n",""), 16);
            buffer.write(parseBody(inputStream, length));
            if (length!=0) {
                inputStream.readByte();
                inputStream.readByte();
            }
        }
        return buffer.toByteArray();
    }

    private byte[] parseBody(DataInputStream inputStream, int length) throws IOException {
        int byteCount = 0;
        byte[] bytes = new byte[length];
        while (byteCount != length) {
            byteCount += inputStream.read(bytes, byteCount, length - byteCount);
        }
        return bytes;
    }

    public void close() {
        try {
            this.socket.close();
        }catch (IOException e){
            e.printStackTrace();
        }
        this.socket = null;
    }

}
