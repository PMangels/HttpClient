package http_client;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import http_datastructures.*;

import static java.util.Base64.*;

public class TCPClient
{
    public static void main(String [] args) throws UnsupportedHTTPCommandException, URISyntaxException, IOException, UnsupportedHTTPVersionException, IllegalHeaderException, IllegalResponseException {
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
        Request request = new Request(type, uri.getPath(), HTTPVersion.HTTP11, "");
        Response result = connection.sendRequest(request);

        Document parsedHtml = Jsoup.parse(result.getContent());
        Elements elements = parsedHtml.getElementsByAttribute("src");
        Elements cssStyleSheets = parsedHtml.getElementsByAttributeValue("rel","stylesheet");
        elements.addAll(cssStyleSheets);
        ArrayList<Connection> connections = new ArrayList<>();
        String absolutePath = System.getProperty("user.dir") + "/Files/";
        File directory = new File (absolutePath);
        if (!directory.exists()){
            directory.mkdir();
        }
        for (Element element: elements){
            Response response;
            URI uriElement;
            if (element.attr("rel").equals("stylesheet")){
                uriElement = new URI(element.attr("href"));
            } else {
                uriElement = new URI(element.attr("src"));
            }
            String filename;
            try {
                String[] pathSplit = uriElement.getPath().split("/");
                filename = pathSplit[pathSplit.length - 1];
            } catch (IndexOutOfBoundsException e){
                filename = uriElement.getPath();
            }
            Request req = new Request(RequestType.GET, uriElement.getPath(), HTTPVersion.HTTP11, "");
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
                response = resource_connection.sendRequest(req);
            }
            List<String> imageExtensions = Arrays.asList("jpeg", "jpg","png", "bmp", "wbmp", "gif");
            String extension;
            if (filename.contains(".")){
                try {
                    String[] filenameSplit = filename.split("\\.");
                    extension = filenameSplit[filenameSplit.length - 1].toLowerCase();
                } catch (IndexOutOfBoundsException e){
                    extension = "";
                }
            } else {
                extension = "";
            }
            int length_extension;
            if (extension.length()==0){
                length_extension = 0;
            } else{
                length_extension = (extension.length()+1);
            }
            String saveName = filename.substring(0,filename.length()-length_extension);
            File file;
            if (extension.length()!=0)
                file = new File(absolutePath +saveName+"."+extension);
            else
                file = new File(absolutePath+saveName);
            int version = 1;
            while (!file.createNewFile()){
                saveName = filename.substring(0,filename.length()-length_extension).concat("_"+version);
                if (extension.length()!=0)
                    file = new File(absolutePath +saveName+"."+extension);
                else
                    file = new File(absolutePath+saveName);
                version+=1;
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
                if (bufferedImage == null){ // Shouldn't happen but there are some files that the ImageIO.read doesn't always work.
                    try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file))) {
                        if (extension.length()!=0)
                            System.out.println("Couldn't decode file: Wrote dummy file to: "+saveName+"."+extension);
                        else
                            System.out.println("Couldn't decode file: Wrote dummy file to: "+saveName);

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
            if (element.attr("rel").equals("stylesheet")){
                if (extension.length()!=0) {
                    element.attr("href", saveName + "." + extension);
                    System.out.println("Wrote resource to file: " + saveName + "." + extension);
                }else {
                    element.attr("href", saveName);
                    System.out.println("Wrote resource to file: " + saveName);
                }            }
            else{
                if (extension.length()!=0) {
                    element.attr("src", saveName + "." + extension);
                    System.out.println("Wrote resource to file: " + saveName + "." + extension);
                }else {
                    element.attr("src", saveName);
                    System.out.println("Wrote resource to file: " + saveName);
                }
            }
        }
        result.setContent(parsedHtml.toString());
        writeHtmlToFile(result, absolutePath);
    }

    public static void writeHtmlToFile(Response result, String absolutePath) {
        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(absolutePath+"webpage.html"))) {
            bufferedWriter.write(result.getContent());
            System.out.println("Wrote page to file: webpage.html");
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println();
        System.out.println(result.getContent());
    }

}

