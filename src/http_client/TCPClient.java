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
        private static List<Connection> connections = new ArrayList<>();

        private static int resourceCounter = 0;

        private static final String absolutePath = System.getProperty("user.dir") + "/Files/";

        private static final List<String> imageExtensions = Arrays.asList("jpeg", "jpg","png", "bmp", "wbmp", "gif");

        public static void main(String [] args) throws UnsupportedHTTPCommandException, URISyntaxException, IOException, UnsupportedHTTPVersionException, IllegalHeaderException, IllegalResponseException {
            try {
                Request request = parseRequest(args);
                Response result = connections.get(0).sendRequest(request);

                File directory = new File(absolutePath);
                if (!directory.exists()) {
                    directory.mkdir();
                }

                if (303 == result.getStatusCode()){
                    result = handleRedirect(request,result);
                }
                if (request.getType() != RequestType.HEAD) {
                    if ((!result.hasHeader("content-type") || (result.getHeader("content-type")).contains("text/html")) && !result.getContent().isEmpty()) {
                        handleHtmlPage(result);

                    }else{
                        handleOtherResponse(request, result);
                    }

                    System.out.println();
                }
                System.out.println(result.toString());
            }
            finally {
                connections.forEach(Connection::close);
            }
        }

        private static Response handleRedirect(Request request, Response result) throws IllegalHeaderException, IllegalResponseException, UnsupportedHTTPVersionException, IOException {
            String newLocation = result.getHeader("location");
            System.out.println("Redirecting to "+newLocation);
            Request newRequest = new Request(request.getType(),newLocation,request.getVersion());
            return connections.get(0).sendRequest(newRequest);
        }

        private static void handleHtmlPage(Response result) throws URISyntaxException, IOException, IllegalResponseException, UnsupportedHTTPVersionException, IllegalHeaderException {
            Document parsedHtml = Jsoup.parse(result.getContent());
            Elements elements = parsedHtml.getElementsByAttribute("src");
            Elements cssStyleSheets = parsedHtml.getElementsByAttributeValue("rel", "stylesheet");
            elements.addAll(cssStyleSheets);

            for (Element element : elements) {
                fetchElement(element);
            }

            result.setContent(parsedHtml.toString(), result.getHeader("content-type"));

            try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(absolutePath + "webpage.html"))) {
                bufferedWriter.write(result.getContent());
                System.out.println("Wrote page to file: webpage.html");
            }
        }

        private static void handleOtherResponse(Request request, Response result) throws IOException {
            String extension = parseExtension(request.getPath());
            if (!extension.isEmpty()){
                extension = "." + extension;
            }
            File file = new File(absolutePath + "response" + extension);
            if (!extension.isEmpty() && imageExtensions.contains(extension.substring(1))){
                writeImage(result.getContent(), file, extension.substring(1));
            }
            else{
                writeFile(result.getContent(), file);
            }
            System.out.println("Wrote page to file: response" + extension);
        }

        private static void fetchElement(Element element) throws URISyntaxException, IOException, IllegalResponseException, UnsupportedHTTPVersionException, IllegalHeaderException {
            resourceCounter++;
            URI uriElement;
            boolean href = element.attr("rel").equals("stylesheet");
            if (href){
                uriElement = new URI(element.attr("href"));
            } else {
                uriElement = new URI(element.attr("src"));
            }

            Request req = new Request(RequestType.GET, uriElement.getPath(), HTTPVersion.HTTP11);
            String host = uriElement.getHost();
            Connection connection = getConnection(host);
            Response response = connection.sendRequest(req);

            String extension = parseExtension(uriElement);
            String filename = String.valueOf(resourceCounter);
            if (!extension.isEmpty())
                filename += "." + extension;
            File file = new File(absolutePath + filename);


            if (imageExtensions.contains(extension)){
                writeImage(response.getContent(), file, extension);
            }
            else{
                writeFile(response.getContent(), file);
            }
            if (href){
                element.attr("href", filename);
            }else{
                element.attr("src", filename);
            }
            System.out.println("Wrote resource to file: " + file.getName());
        }

        private static Connection getConnection(String host) throws IOException {
            if (host == null) {
                return connections.get(0);
            }
            for (Connection activeConnection : connections){
                if (host.equals(activeConnection.getHost())){
                    return activeConnection;
                }
            }
            Connection connection = new Connection(host,80);
            connections.add(connection);
            return connection;
        }

        private static String parseExtension(URI uriElement) {
            return parseExtension(uriElement.getPath());
        }

        private static Request parseRequest(String [] args) throws UnsupportedHTTPCommandException, URISyntaxException, IOException {
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

            TCPClient.connections.add(new Connection(uri.getHost(), port));
            if (type.equals(RequestType.POST)||type.equals(RequestType.PUT)){
                System.out.println("Enter file data here. Close with CRLF.CRLFCRLF:");
                Scanner userInput = new Scanner(System.in);
                String content = userInput.nextLine();
                while (true) {
                    String newLine = userInput.nextLine();
                    content = content.concat("\r\n"+newLine);
                    if (content.endsWith("\r\n.\r\n")){
                        content = content.substring(0,content.length()-5);
                        break;
                    }
                }
                return new Request(type, uri.getPath(), HTTPVersion.HTTP11, content, "text/plain");
            }else{
                return new Request(type, uri.getPath(), HTTPVersion.HTTP11);
            }
        }

        private static String parseExtension(String path) {
            String filename;
            try {
                String[] pathSplit = path.split("/");
                filename = pathSplit[pathSplit.length - 1];
            } catch (IndexOutOfBoundsException e){
                filename = path;
            }
            if (filename.contains(".")){
                try {
                    String[] filenameSplit = filename.split("\\.");
                    return filenameSplit[filenameSplit.length - 1].toLowerCase();
                } catch (IndexOutOfBoundsException e){
                    return  "";
                }
            } else {
                return  "";
            }
        }

        private static void writeFile(String content, File file) throws IOException {
            try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file))) {
                bufferedWriter.write(content);
            }
        }

        private static void writeImage(String content, File file, String extension) throws IOException {
            byte[] byteString = getDecoder().decode(content.getBytes(StandardCharsets.UTF_8));
            BufferedImage bufferedImage;
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteString);
            bufferedImage = ImageIO.read(byteArrayInputStream);

            if (bufferedImage != null) {
                ImageIO.write(bufferedImage, extension, file);
            }else{
                System.out.println("The requested image: " + file.getPath() + " could not be downloaded from the server.");
            }
        }

    }

