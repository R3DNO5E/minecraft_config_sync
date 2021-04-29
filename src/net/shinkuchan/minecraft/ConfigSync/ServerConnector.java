package net.shinkuchan.minecraft.ConfigSync;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

public class ServerConnector {
    private String xmlString;
    private final URL serverURL;
    private FileSystem.FSNode files;

    public ServerConnector(String url) throws java.net.MalformedURLException {
        serverURL = new URL(url);
    }

    public void fetch() throws java.io.IOException {
        System.out.print("Fetching config files from: " + serverURL.toString() + " ...");
        HttpsURLConnection con = (HttpsURLConnection) serverURL.openConnection();
        con.setRequestMethod("GET");
        BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = br.read()) != -1) {
            sb.append((char) c);
        }
        br.close();
        xmlString = sb.toString();
        System.out.println("received " + xmlString.length() + " bytes");
        System.out.println("Parsing XML...");
        try {
            files = FileSystem.TreeBuilder.build(xmlString);
            System.out.println("done");
        } catch (Exception e) {
            e.printStackTrace();
            files = new FileSystem.FSNode();
            System.out.println("error");
        }
    }

    public String getXmlString() {
        return xmlString;
    }

    public FileSystem.FSNode getFSTree() {
        return files;
    }
}
