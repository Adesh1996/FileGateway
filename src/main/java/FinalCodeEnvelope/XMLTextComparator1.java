package FinalCodeEnvelope;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.ComparisonType;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.Difference;
import org.xmlunit.diff.ElementSelectors;
import org.xmlunit.input.WhitespaceStrippedSource;
import org.xmlunit.util.Predicate;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.*;
import java.util.*;

public class XMLTextComparator1 {

    public static void main(String[] args) throws Exception {
      
        
        String actualPath= "C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\Comparison\\Batch_BNYGB22XXX_Actual.xml";
		String expectedPath="C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\Comparison\\Batch_BNYGB22XXX_Automation.xml" ;
		String outputPath= "C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\Comparison\\Output\\output_diff.txt";

        compareXMLFiles(actualPath, expectedPath, outputPath);
    }

    public static void compareXMLFiles(String file1Path, String file2Path, String outputPath) throws Exception {
        // Normalize XML content from txt files
        String xml1 = normalizeXML(file1Path);
        String xml2 = normalizeXML(file2Path);

        // Define differences to ignore (optional)
        List<String> tagsToExclude = Arrays.asList("InstdAmt"); // Add InstdAmt to be treated as optional
        final Set<String> excludeTagsSet = new HashSet<String>(tagsToExclude);

        Predicate<Node> nodeFilter = new Predicate<Node>() {
            public boolean test(Node node) {
                return !excludeTagsSet.contains(node.getNodeName());
            }
        };

        // Compare
        Diff diff = DiffBuilder
                .compare(Input.fromString(xml1))
                .withTest(Input.fromString(xml2))
                .ignoreWhitespace()
                .ignoreComments()
                .withNodeFilter(nodeFilter)
                .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byNameAndText))
                .checkForSimilar()
                .build();

        // Output differences
        writeTextReport(diff, outputPath);
    }

    private static String normalizeXML(String filePath) throws Exception {
        File inputFile = new File(filePath);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(inputFile);
        doc.normalizeDocument();

        // Use a Transformer to output the XML as a string with proper indentation
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));

        return writer.toString();
    }

    private static void writeTextReport(Diff diff, String outputPath) throws IOException {
        FileWriter writer = null;
        try {
            writer = new FileWriter(outputPath);
            if (!diff.hasDifferences()) {
                writer.write("No differences found.\n");
            } else {
                writer.write("Differences found:\n");
                int count = 1;
                for (Difference difference : diff.getDifferences()) {
                    String type = difference.getComparison().getType().toString();
                    String xpath = "";
                    try {
                        xpath = difference.getComparison().getControlDetails().getXPath();
                        if (xpath == null || xpath.isEmpty()) {
                            xpath = difference.getComparison().getTestDetails().getXPath();
                        }
                    } catch (Exception e) {
                        xpath = "(not available)";
                    }

                    writer.write(count + ". Type: " + type + "\n");
                    writer.write("   XPath: " + xpath + "\n");
                    writer.write("   Detail: " + difference.toString() + "\n\n");
                    count++;
                }
            }
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
}
