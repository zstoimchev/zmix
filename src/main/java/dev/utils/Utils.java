package dev.utils;

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

    public static String ppp() {
        return "e23e";
    }

    public static String extractHtml(String response) {
        String lowercaseResponse = response.toLowerCase();
        int start = lowercaseResponse.indexOf("<!doctype html");
        if (start == -1) start = lowercaseResponse.indexOf("<html");
        return (start != -1) ? response.substring(start) : response;
    }
}
