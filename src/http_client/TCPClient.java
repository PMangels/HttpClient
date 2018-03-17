package http_client;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.util.Base64.*;

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
        } catch (IllegalArgumentException e){
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
        String absolutePath = System.getProperty("user.dir") + "/Files/";
        System.out.println(absolutePath);
        for (Element element: elements){
            URI uriElement = new URI(element.attr("src"));
            String filename;
            try {
                String[] pathSplit = uriElement.getPath().split("/");
                filename = pathSplit[pathSplit.length - 1];
            } catch (IndexOutOfBoundsException e){
                filename = uriElement.getPath();
            }
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
                    resource_connection = new Connection(resource_host,80);
                    connections.add(resource_connection);
                }
                response = connection.sendRequest(req);
            }
            List<String> imageExtensions = Arrays.asList("jpeg", "jpg","png", "bmp", "wbmp", "gif");
            String extension;
            try {
                String[] filenameSplit = filename.split("\\.");
                extension = filenameSplit[filenameSplit.length - 1].toLowerCase();
            } catch (IndexOutOfBoundsException e){
                extension = "";
            }
            File file = new File(absolutePath+filename);
            if (!file.createNewFile()){
                System.out.println("Overwriting file: "+filename);
            }
            if (imageExtensions.contains(extension)){
                byte[] byteString = getDecoder().decode(response.getContent().getBytes(StandardCharsets.UTF_8));
                BufferedImage bufferedImage;
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteString);
                try {
                    bufferedImage = ImageIO.read(byteArrayInputStream);
                } catch (IOException e) {
                    bufferedImage = null;
                    e.printStackTrace();
                }
                //Todo: can't decode some files although other files with same extension do work
                if (bufferedImage == null){
                    try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file))) {
                        System.out.println("Couldn't decode file: Wrote dummy file to: "+filename);
                        bufferedWriter.write("");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else{
                    try {
                        ImageIO.write(bufferedImage, extension, file);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            else{
                try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file))) {
                    bufferedWriter.write(response.getContent());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            element.attr("src",filename);
            System.out.println("Wrote resource to file: "+filename);
        }
        result.setContent(parsedHtml.toString());
        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(absolutePath+"webpage.html"))) {


            bufferedWriter.write(result.getContent());

            System.out.println("Wrote page to file: webpage.html");

        } catch (IOException e) {

            e.printStackTrace();
        }
    }
}

