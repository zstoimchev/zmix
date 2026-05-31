package dev.utils;

import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/*
 * Utility class for static methods shared across all the methods
 * */
public class Utils {
    public static String encodeBytesToString(byte[] bytes) {
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] decodeStringToBytes(String string) {
        return java.util.Base64.getDecoder().decode(string);
    }

    public static String extractHtml(String response) {
        String lowercaseResponse = response.toLowerCase();
        int start = lowercaseResponse.indexOf("<!doctype html");
        if (start == -1) start = lowercaseResponse.indexOf("<html");
        if (start == -1) return response;
        int end = lowercaseResponse.lastIndexOf("</html>");
        if (end == -1) return response.substring(start);
        return response.substring(start, end + 7);
    }

    public static String saveHttpResponseNaive(String response, String fileName) throws FileNotFoundException {
        String html = extractHtml(response);
        try (PrintWriter out = new PrintWriter(fileName)) {
            out.println(html);
        }
        return html;
    }

    public static String buildHttpGet(String host) {
        return "GET / HTTP/1.1\r\n" +
                "Host: " + host + "\r\n" +
                "User-Agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36\r\n" +
                "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n" +
                "Accept-Language: en-US,en;q=0.5\r\n" +
                "Connection: close\r\n" +
                "\r\n";
    }

    public static byte[] sendHttpRequest(String host, String port, byte[] httpRequestData) throws IOException {
        int portNum = Integer.parseInt(port);
        Socket socket;

        if (portNum == 443) {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            socket = factory.createSocket(host, portNum);
//            logger.debug("Created SSL socket to {}:{}", host, portNum);
        } else {
            socket = new Socket(InetAddress.getByName(host), portNum);
//            logger.debug("Created plain socket to {}:{}", host, portNum);
        }

        socket.getOutputStream().write(httpRequestData);
        socket.getOutputStream().flush();

        StringBuilder response = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String line;
        while ((line = br.readLine()) != null) response.append(line).append("\r\n");
        br.close();
        socket.close();
        return response.toString().getBytes(StandardCharsets.UTF_8);
    }
}
