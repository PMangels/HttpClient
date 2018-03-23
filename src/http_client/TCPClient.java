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

    /**
     * A simple HTTP client with support for sending GET, PUT, POST and HEAD requests.
     * Embedded images, stylesheets and javascript files will be fetched automatically.
     */
    public class TCPClient
    {
        private static List<Connection> connections = new ArrayList<>();

        private static int resourceCounter = 0;

        private static final String absolutePath = System.getProperty("user.dir") + "/Files/";

        private static final List<String> imageExtensions = Arrays.asList("jpeg", "jpg","png", "bmp", "wbmp", "gif");

        /**
         * Send an HTTP request to the provided server and process its response.
         * 30x redirects will be followed.
         * @param args "HTTPCommand URI Port"
         *             Supported HTTP commands are GET, POST, HEAD and PUT.
         *             PUT and POST requests will ask for body contents.
         * @throws UnsupportedHTTPCommandException The provided HTTP method is not supported by this client.
         * @throws URISyntaxException The provided URI was not valid.
         * @throws IOException An IO Exception occurred trying to send this request.
         * @throws UnsupportedHTTPVersionException The HTTP version returned by the server is not supported by this client.
         * @throws IllegalHeaderException One of the headers returned by the server was malformed and could not be parsed.
         * @throws IllegalResponseException The response returned by the server was malformed and could not be parsed.
         */
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

        /**
         * Handles a HTTP redirect by redirecting to the provided location header.
         * @param request The original request made to the server.
         * @param result The response that demanded a redirect.
         * @return The response from the url we were redirected to.
         * @throws IllegalHeaderException One of the headers in the response was malformed.
         * @throws IllegalResponseException The received response was malformed.
         * @throws UnsupportedHTTPVersionException The received response uses an HTTP version not supported by this client.
         * @throws IOException An IOException occured while communicating to the server.
         */
        private static Response handleRedirect(Request request, Response result) throws IllegalHeaderException, IllegalResponseException, UnsupportedHTTPVersionException, IOException {
            String newLocation = result.getHeader("location");
            System.out.println("Redirecting to "+newLocation);
            Request newRequest = new Request(request.getType(),newLocation,request.getVersion());
            return connections.get(0).sendRequest(newRequest);
        }

        /**
         * Downloads all embedded objects in the received response and writes them to file.
         * Writes the received response contents to a file, updating the links to the embedded objects
         * with the local downloaded objects.
         * @param result The received HTTP response to parse.
         * @throws URISyntaxException An invalid URI was encountered while parsing this page.
         * @throws IOException An IOException occurred while communicating with the server or while writing to disk.
         * @throws IllegalResponseException An malformed response was received.
         * @throws UnsupportedHTTPVersionException A response using an HTTP version not supported by this client was retrieved.
         * @throws IllegalHeaderException A response with a malformed header was retrieved.
         */
        private static void handleHtmlPage(Response result) throws URISyntaxException, IOException, IllegalResponseException, UnsupportedHTTPVersionException, IllegalHeaderException {
            // Get embedded objects
            Document parsedHtml = Jsoup.parse(result.getContent());
            Elements elements = parsedHtml.getElementsByAttribute("src");
            Elements cssStyleSheets = parsedHtml.getElementsByAttributeValue("rel", "stylesheet");
            elements.addAll(cssStyleSheets);

            for (Element element : elements) {
                fetchElement(element);
            }

            // Set the content to the document with updated local urls.
            result.setContent(parsedHtml.toString(), result.getHeader("content-type"));

            try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(absolutePath + "webpage.html"))) {
                bufferedWriter.write(result.getContent());
                System.out.println("Wrote page to file: webpage.html");
            }
        }

        /**
         * Write the contents of the provided response to a file.
         * @param request The original HTTP request for this file.
         * @param result The received response of which the contents need to be written to file.
         * @throws IOException An IOException occured while trying to write to disk.
         */
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

        /**
         * Retrieve the embedded element from the server and write it to a file,
         * using this.resourceCounter as filename.
         * Increases this.resourceCounter by one.
         * If a connection to this host is already up, it will be used to retrieve this file,
         * otherwise a new connection will be created and saved in the list of connections.
         * @param element The HTML element containing the url to be retrieved.
         * @throws URISyntaxException This html element does not contain a valid url.
         * @throws IOException An IOException occured while communicating with the server or writing to disk.
         * @throws IllegalResponseException A malformed response was returned from the server.
         * @throws UnsupportedHTTPVersionException The returned response uses an HTTP version not supported by this client.
         * @throws IllegalHeaderException The returned response contained a malformed HTTP header.
         */
        private static void fetchElement(Element element) throws URISyntaxException, IOException, IllegalResponseException, UnsupportedHTTPVersionException, IllegalHeaderException {
            // Increase the counter for different filenames.
            resourceCounter++;
            URI uriElement;
            boolean href = element.attr("rel").equals("stylesheet");
            if (href){
                uriElement = new URI(element.attr("href"));
            } else {
                uriElement = new URI(element.attr("src"));
            }

            // Get a connection to this host and request the object.
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

            // Update the url in the HTML to the local url.
            if (href){
                element.attr("href", filename);
            }else{
                element.attr("src", filename);
            }
            System.out.println("Wrote resource to file: " + file.getName());
        }

        /**
         * Retrieves an existing connection object for the provided host if one could be found in
         * this.connections. If a connection to this host does not yet exist it will be created,
         * added to the list and returned.
         * @param host The host adress for which a connection is required.
         * @return A connection to the provided host.
         * @throws IOException An IOException occurred while trying to create a connection to this host.
         */
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

        /**
         * Get the extension of the file in this url.
         * @param uriElement The url to the file of which the extension is requested.
         * @return The extension of the file at the url. Or the empty string if it has no extension.
         */
        private static String parseExtension(URI uriElement) {
            return parseExtension(uriElement.getPath());
        }

        /**
         * Parses command-line arguments for creating a request.
         * Creates a connection to the provided host and port and adds it to the list of connections.
         * In case of PUT or POST requests, extra input from the user is requested.
         * @param args The command line arguments in the format: "HTTPCommand URI Port"
         * @return A request object containing the provided data.
         * @throws UnsupportedHTTPCommandException The supplied HTTP method is not supported by this client.
         * @throws URISyntaxException The supplied URI is not valid.
         * @throws IOException An IOException occurred while trying to create a connection to the server.
         */
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

            // Create a connection to the provided host and add it to the list of connections
            TCPClient.connections.add(new Connection(uri.getHost(), port));
            if (type.equals(RequestType.POST)||type.equals(RequestType.PUT)){
                // Request body input from user.
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

        /**
         * Return the file extension of the provided path string.
         * @param path The path of the file of which the extension has to be parsed.
         * @return The extension of the file or an empty string if the file has no extension.
         */
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

        /**
         * Write the provided content to the provided file.
         * @param content The content to be written to file.
         * @param file The file to be written to.
         * @throws IOException An IOException occurred while writing to file.
         */
        private static void writeFile(String content, File file) throws IOException {
            try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file))) {
                bufferedWriter.write(content);
            }
        }

        /**
         * Write the provided image to file.
         * @param content The image contents to be written.
         * @param file The file to be written to.
         * @param extension The extension of the image to be written.
         * @throws IOException An IOException occurred while writing to disk.
         */
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

