package http_client;

import java.io.*;
import java.net.*;

/**
 * copy of code from prof's slides...
 */
public class TCPClient
{
    public static void main(String [] args) throws UnsupportedHTTPCommandException, URISyntaxException, IOException {
        if (args.length != 3)
            throw new IllegalArgumentException();

        RequestType type;
        try {
            type = RequestType.valueOf(args[0]);
        }catch (IllegalArgumentException e){
            throw new UnsupportedHTTPCommandException();
        }

        String rawUri = args[1];
        if (!rawUri.startsWith("http://"))
            rawUri = "http://" + rawUri;

        URI uri = new URI(rawUri);

        int port = Integer.parseInt(args[2]);

        Connection connection = new Connection(uri.getHost(), port);
        Request request = new Request(type, uri.getPath());

        System.out.println(connection.sendRequest(request));
    }
}

