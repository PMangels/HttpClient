package http_client;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.*;
import java.util.*;

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
        Elements elements = parsedHtml.getElementsByAttribute("src");
        Response response;
        ArrayList<Connection> connections = new ArrayList<>();
        for (Element element: elements){
            URI uriElement = new URI(element.attr("src"));
            String filename = uriElement.getPath();
            Request req = new Request(RequestType.GET, uriElement.getPath());
            String resource_host = uriElement.getHost();
            if (resource_host == null || resource_host.equals(connection.getHost())) {
                response = connection.sendRequest(req);
            }
            else{
                Connection resource_connection = null;
                for (Connection active_connection: connections) {
                    if (resource_host.equals(active_connection.getHost())){
                        resource_connection = active_connection;
                        break;
                    }
                }
                if (resource_connection == null) {
                    resource_connection = new Connection(resource_host,8000);
                    connections.add(resource_connection);
                }
                response = connection.sendRequest(req);
            }
            try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(filename))) {


                bufferedWriter.write(response.getContent());

                System.out.println("Wrote resource to file: "+filename);

            } catch (IOException e) {

                e.printStackTrace();
            }
            // TODO: rename url in main document element.attr("src", "") voor directory mappings te laten kloppen
        }
        //TODO: write modified main document to file
        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(rawUri))) {


            bufferedWriter.write(result.getContent());

            System.out.println("Wrote resource to file: "+rawUri);

        } catch (IOException e) {

            e.printStackTrace();
        }
    }
}

