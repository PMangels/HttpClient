package http_client;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.*;
import java.net.*;

/**
 * copy of code from prof's slides...
 */
public class TCPClient
{
    public static void main(String [] args) throws UnsupportedHTTPCommandException, URISyntaxException, IOException, UnsupportedHTTPVersionException {
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

        Response result = connection.sendRequest(request);
        Document parsedHtml = Jsoup.parse(result.getContent());
        for (Element element: parsedHtml.getElementsByTag("img")){
            String url = element.attr("src");
            Request req = new Request(RequestType.GET, url);
            Response response = connection.sendRequest(req);
            // write to file
            // rename url in main document element.attr("src", "") voor directory mappings te laten kloppen
        }
        //write modified main document to file
    }
}

