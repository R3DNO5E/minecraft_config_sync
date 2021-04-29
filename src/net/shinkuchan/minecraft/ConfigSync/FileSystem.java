package net.shinkuchan.minecraft.ConfigSync;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

public class FileSystem {
    public static class FSNode {
        public final String name, path;
        public final Date update;

        public FSNode() {
            name = "";
            path = "";
            update = new Date(0);
        }

        protected static String getFirstTagContent(final Element element, final String tag) {
            NodeList n = element.getElementsByTagName(tag);
            return n.getLength() > 0 ? n.item(0).getTextContent() : "";
        }

        public FSNode(final Element element) {
            name = element.getAttribute("name");
            path = getFirstTagContent(element, "path");
            update = new Date(Long.parseLong(element.getAttribute("update")) * 1000);
        }

        protected void createDirectoriesIfNotExists(final Path p) throws IOException {
            System.out.print("Creating directory recursively: " + p.toString() + " ...");
            if (!Files.exists(p)) {
                System.out.println("directory newly created");
                Files.createDirectories(p);
            } else {
                System.out.println("directory already exists");
            }
        }

        protected void createDirectoryIfNotExists(final Path p) throws IOException {
            System.out.print("Creating directory: " + p.toString() + " ...");
            if (!Files.exists(p)) {
                System.out.println("directory newly created");
                Files.createDirectory(p);
            } else {
                System.out.println("directory already exists");
            }
        }

        protected void overwriteFile(final Path path, final byte[] data) throws IOException {
            System.out.print("Writing file: " + path.toString() + " ...");
            if (!Files.exists(path)) {
                System.out.println("written " + data.length + " bytes");
                Files.write(path, data, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
            } else {
                System.out.println("overwritten " + data.length + " bytes");
                Files.write(path, data, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        }

        public void write(final String prefix) throws IOException {
            Path p = Paths.get(prefix, path).getParent();
            createDirectoriesIfNotExists(p);
        }

        @Override
        public String toString() {
            return "FSNode{" +
                    "name='" + name + "'\n" +
                    ", path='" + path + "'\n" +
                    ", update=" + update + "'\n" +
                    "}\n";
        }

    }

    public static class File extends FSNode {
        public final byte[] content;
        public static String tagName = "file";

        private boolean verifyFileHash(final byte[] data, final byte[] hash, final String algo) {
            try {
                return Arrays.equals(MessageDigest.getInstance(algo).digest(data), hash);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                return false;
            }
        }

        private byte[] hexToByte(final String hex_) {
            String hex = hex_.length() % 2 == 0 ? hex_ : hex_ + '0';
            int len = hex.length() / 2;
            byte[] ret = new byte[len];
            for (int i = 0; i < len; i++) {
                ret[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            return ret;
        }

        public File(final Element element) {
            super(element);
            if (element.getTagName().equals(tagName)) {
                byte[] tb = Base64.getDecoder().decode(getFirstTagContent(element, "content"));
                NodeList n = element.getElementsByTagName("hash");
                boolean hashOK = false;
                if (n.getLength() > 0) {
                    Element hash = (Element) n.item(0);
                    if (hash.getAttribute("algo").equals("sha-256")) {
                        System.out.print("Verifying file hash (sha-256): " + path + " ...");
                        hashOK = verifyFileHash(tb, hexToByte(hash.getTextContent()), "SHA-256");
                        System.out.println(hashOK ? "OK" : "NG");
                    } else {
                        System.out.println("Unknown hash type: " + path + " ...skipping");
                    }
                }
                content = hashOK ? tb : new byte[0];
            } else {
                content = new byte[0];
            }
        }

        @Override
        public void write(final String prefix) throws IOException {
            super.write(prefix);
            overwriteFile(Paths.get(prefix, path), content);
        }

        @Override
        public String toString() {
            return "Directory{" +
                    "name='" + name + "'\n" +
                    ", path='" + path + "'\n" +
                    ", update=" + update + "'\n" +
                    ", size=" + content.length + "'\n" +
                    "}\n";
        }
    }

    public static class Directory extends FSNode {
        public final FSNode[] sib;
        public static String tagName = "directory";

        public Directory(final Element element) {
            super(element);
            if (element.getTagName().equals(tagName)) {
                NodeList n = element.getElementsByTagName("sub");
                if (n.getLength() == 0) {
                    sib = new FSNode[0];
                } else {
                    NodeList m = n.item(0).getChildNodes();
                    sib = new FSNode[m.getLength()];
                    for (int i = 0; i < m.getLength(); i++) {
                        sib[i] = TreeBuilder.buildElement((Element) m.item(i));
                    }
                }
            } else {
                sib = new FSNode[0];
            }
        }

        @Override
        public void write(final String prefix) throws IOException {
            super.write(prefix);
            createDirectoryIfNotExists(Paths.get(prefix, path));
            for (FSNode e : sib) {
                e.write(prefix);
            }
        }

        @Override
        public String toString() {
            return "Directory{" +
                    "name='" + name + "'\n" +
                    ", path='" + path + "'\n" +
                    ", update=" + update + "'\n" +
                    ", sib=" + Arrays.toString(sib) + "'\n" +
                    "}\n";
        }
    }

    public static class TreeBuilder {
        public static FSNode buildElement(final Element element) {
            if (element.getTagName().equals(Directory.tagName)) {
                return new Directory(element);
            } else if (element.getTagName().equals(File.tagName)) {
                return new File(element);
            } else {
                return new FSNode(element);
            }
        }

        public static FSNode build(final String xml) throws ParserConfigurationException, IOException, SAXException {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            Element root = doc.getDocumentElement();
            return buildElement(root);
        }
    }
}
