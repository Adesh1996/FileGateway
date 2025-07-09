package FinalCodeEnvelope;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.util.*;

public class XmlComparatorWithHtmlReport {

    private static List<String> differences = new ArrayList<String>();

    private static Document loadXmlDocument(String filePath) throws Exception {
        File file = new File(filePath);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(file);
        document.getDocumentElement().normalize();
        return document;
    }

    private static boolean compareNodes(Node node1, Node node2, String path) {
        if (!node1.getNodeName().equals(node2.getNodeName())) {
            differences.add("Different element names at " + path + ": " +
                    node1.getNodeName() + " vs " + node2.getNodeName());
            return false;
        }

        // Compare text content
        String text1 = node1.getTextContent().trim();
        String text2 = node2.getTextContent().trim();
        if (!text1.equals(text2)) {
            differences.add("Different text content at " + path + "/" + node1.getNodeName() +
                    ": \"" + text1 + "\" vs \"" + text2 + "\"");
        }

        // Compare attributes
        if (!compareAttributes(node1, node2, path + "/" + node1.getNodeName())) {
            return false;
        }

        // Compare children
        NodeList children1 = node1.getChildNodes();
        NodeList children2 = node2.getChildNodes();

        int length1 = getElementChildren(children1).size();
        int length2 = getElementChildren(children2).size();
        if (length1 != length2) {
            differences.add("Different number of child elements at " + path + "/" + node1.getNodeName());
            return false;
        }

        List<Node> elementChildren1 = getElementChildren(children1);
        List<Node> elementChildren2 = getElementChildren(children2);

        for (int i = 0; i < elementChildren1.size(); i++) {
            compareNodes(elementChildren1.get(i), elementChildren2.get(i), path + "/" + node1.getNodeName());
        }

        return true;
    }

    private static List<Node> getElementChildren(NodeList nodeList) {
        List<Node> elements = new ArrayList<Node>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                elements.add(child);
            }
        }
        return elements;
    }

    private static boolean compareAttributes(Node node1, Node node2, String path) {
        NamedNodeMap attrs1 = node1.getAttributes();
        NamedNodeMap attrs2 = node2.getAttributes();

        if (attrs1 == null && attrs2 == null) return true;

        if (attrs1.getLength() != attrs2.getLength()) {
            differences.add("Different number of attributes at " + path);
            return false;
        }

        for (int i = 0; i < attrs1.getLength(); i++) {
            Node attr1 = attrs1.item(i);
            Node attr2 = attrs2.getNamedItem(attr1.getNodeName());
            if (attr2 == null || !attr1.getNodeValue().equals(attr2.getNodeValue())) {
                differences.add("Attribute mismatch at " + path + ": " +
                        attr1.getNodeName() + "=\"" + attr1.getNodeValue() +
                        "\" vs \"" + (attr2 != null ? attr2.getNodeValue() : "null") + "\"");
            }
        }
        return true;
    }

    private static void generateHtmlReport(String outputPath, boolean isIdentical) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write("<html><head><title>XML Comparison Report</title>");
            writer.write("<style>body{font-family:Arial;} .ok{color:green;} .fail{color:red;}</style></head><body>");
            writer.write("<h1>XML Comparison Report</h1>");
            writer.write("<p>Status: <strong class='" + (isIdentical ? "ok" : "fail") + "'>" +
                    (isIdentical ? "Files are identical." : "Files are different.") + "</strong></p>");

            if (!differences.isEmpty()) {
                writer.write("<h2>Differences:</h2><ul>");
                for (String diff : differences) {
                    writer.write("<li>" + diff + "</li>");
                }
                writer.write("</ul>");
            }

            writer.write("</body></html>");
        }
    }

    public static void main(String[] args) {
    	
    	String file1= "C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\Comparison\\Batch_BNYGB22XXX_Actual.txt";
		String file2="C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\Comparison\\Batch_BNYGB22XXX_Automation.txt" ;
		String reportOutput= "C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\Comparison\\Output\\output_diff.html";
        

        try {
            Document doc1 = loadXmlDocument(file1);
            Document doc2 = loadXmlDocument(file2);
            boolean result = compareNodes(doc1.getDocumentElement(), doc2.getDocumentElement(), "");

            generateHtmlReport(reportOutput, differences.isEmpty());

            System.out.println("Comparison done. HTML report generated at: " + reportOutput);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
