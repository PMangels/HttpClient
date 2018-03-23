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

    /**
     * Retrieve the host for this connection.
     * @return The host for this connection.
     */
    public String getHost() {
        return host;
    }

    /**
     * Initialize a new connection to the provided host and port.
     * @param host The host to connect to.
     * @param port The port to connect to.
     * @throws IOException An IOException occurred while trying to connect to this server.
     */
    public Connection(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        this.socket = new Socket(host, port);
    }

    /**
     * Send the provided request over this connection and retrieve the response.
     * If the connection to the server is down, it will be restarted.
     * @param request The request to send.
     * @return The response retrieved from the server.
     * @throws IOException An IOException occurred while communicating to the server.
     * @throws UnsupportedHTTPVersionException The response returned from the server uses an HTTP version not supported by this client.
     * @throws IllegalHeaderException The response returned from the server contains a malformed header.
     * @throws IllegalResponseException The response returned from the server is malformed.
     */
    public Response sendRequest(Request request) throws IOException, UnsupportedHTTPVersionException, IllegalHeaderException, IllegalResponseException {
        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
        DataInputStream inputStream = new DataInputStream(socket.getInputStream());
        request.addHeader("Host", this.host + ":" + this.port);

        outputStream.writeBytes(request.toString());

        StringBuilder responseBuffer = new StringBuilder();

        // See if our socket connection is still alive, otherwise create a new one and re-send the request.
        try{
            responseBuffer.append((char) inputStream.readByte());
        } catch (EOFException | SocketException e){
            this.socket = new Socket(this.host, this.port);
            outputStream = new DataOutputStream(socket.getOutputStream());
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream.writeBytes(request.toString());
        }

        // Read all headers
        do {
            // Remove all 100 Continue responses from the server.
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

        // Look for important headers: content-length, content-type and transfer-encoding.
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

        // Read the body
        byte[] bytes;
        if (chunked){
            // Read the body using chunked tranfer encoding.
            bytes = parseBodyChunked(inputStream);
            do{
                responseBuffer.insert(responseBuffer.length()-2,(char) inputStream.readByte());
            }while (!responseBuffer.toString().endsWith("\r\n\r\n\r\n"));
            responseBuffer.delete(responseBuffer.length()-2,responseBuffer.length());
        }else{
            bytes = parseBody(inputStream, length);
        }

        // Convert the body contents to a string.
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

    /**
     * Read an HTTP response body from the provided inputstream using chunked transfer encoding.
     * @param inputStream The inputstream to read the response from.
     * @return The HTTP response body contents.
     * @throws IOException An IOException occurred while communicating with the server.
     */
    private byte[] parseBodyChunked(DataInputStream inputStream) throws IOException {
        int length = -1;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        while (length != 0){
            StringBuilder responseBuffer = new StringBuilder();

            // Read the first line of this chunk.
            while (!responseBuffer.toString().endsWith("\r\n")) {
                responseBuffer.append((char) inputStream.readByte());
            }
            // Find the length of this chunk.
            String[] firstline = responseBuffer.toString().split(";");
            length = Integer.parseInt(firstline[0].replace("\r\n",""), 16);

            // Read the contents of this chunk.
            buffer.write(parseBody(inputStream, length));
            if (length!=0) {
                inputStream.readByte();
                inputStream.readByte();
            }
        }
        return buffer.toByteArray();
    }

    /**
     * Read an HTTP response body from the provided inputstream.
     * @param inputStream The inputstream to read the response from.
     * @param length The content-length to be read.
     * @return The HTTP response body contents.
     * @throws IOException An IOException occurred while communicating with the server.
     */
    private byte[] parseBody(DataInputStream inputStream, int length) throws IOException {
        int byteCount = 0;
        byte[] bytes = new byte[length];
        while (byteCount != length) {
            byteCount += inputStream.read(bytes, byteCount, length - byteCount);
        }
        return bytes;
    }

    /**
     * Close this connection by closing the underlying socket and setting it to null.
     */
    public void close() {
        try {
            this.socket.close();
        }catch (IOException e){
            e.printStackTrace();
        }
        this.socket = null;
    }

}
