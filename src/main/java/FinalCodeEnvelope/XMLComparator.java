package FinalCodeEnvelope;

import org.w3c.dom.Node;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.Difference;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.ElementSelectors;
import org.xmlunit.input.WhitespaceStrippedSource;
import org.xmlunit.util.Predicate;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


/**
 * This is the final code for comparing the two xml files 
 * @author ADMIN
 *
 */

public class XMLComparator {

    public static void main(String[] args) throws Exception {
        String file1 = "C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\Comparison\\Batch_BNYGB22XXX_Actual.txt";
        String file2 = "C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\Comparison\\Batch_BNYGB22XXX_Automation.txt";
        String outputTextPath = "C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\Comparison\\Output\\output_diff.txt";
        String outputHtmlPath = "C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\Comparison\\Output\\output_diff.html";

        // Leave empty to compare everything
        List<String> tagsToExclude = Arrays.asList();

        compareXMLFiles(file1, file2, outputTextPath, outputHtmlPath, tagsToExclude);
    }

    public static void compareXMLFiles(String file1Path, String file2Path,
                                       String outputTextPath, String outputHtmlPath,
                                       List<String> tagsToExclude) throws Exception {

        final Set<String> excludeTagsSet = new HashSet<>(tagsToExclude);

        Predicate<Node> nodeFilter = new Predicate<Node>() {
            public boolean test(Node node) {
                return !excludeTagsSet.contains(node.getNodeName());
            }
        };

        // Enhanced node matching logic
        Diff diff = DiffBuilder.compare(new WhitespaceStrippedSource(Input.fromFile(file1Path).build()))
                .withTest(new WhitespaceStrippedSource(Input.fromFile(file2Path).build()))
                .ignoreComments()
                .ignoreWhitespace()
                .withNodeFilter(nodeFilter)
                .withNodeMatcher(
                        new DefaultNodeMatcher(
                                ElementSelectors.conditionalBuilder()
                                        .whenElementIsNamed("InstdAmt").thenUse(ElementSelectors.byNameAndAllAttributes)
                                        .whenElementIsNamed("CtrlSum").thenUse(ElementSelectors.byName)
                                        .whenElementIsNamed("PmtInf").thenUse(ElementSelectors.byNameAndAllAttributes)
                                        .elseUse(ElementSelectors.byNameAndText)
                                        .build()
                        )
                ) 
                .checkForSimilar() 
                .build();

        writeTextReport(diff, outputTextPath);
        writeHtmlReport(diff, outputHtmlPath);

        System.out.println("Comparison complete.");
        System.out.println("Text output: " + outputTextPath);
        System.out.println("HTML output: " + outputHtmlPath);
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

                    String xpath = sanitizeNamespace(difference.getComparison().getControlDetails().getXPath());
                    if (xpath == null || xpath.isEmpty()) {
                        xpath = sanitizeNamespace(difference.getComparison().getTestDetails().getXPath());
                    }

                    String controlValue = safeValue(difference.getComparison().getControlDetails().getValue());
                    String testValue = safeValue(difference.getComparison().getTestDetails().getValue());
                    String detail = "Expected: " + controlValue + ", Found: " + testValue;

                    writer.write(count + ". Type: " + type + "\n");
                    writer.write("   XPath: " + xpath + "\n");
                    writer.write("   Detail: " + sanitizeNamespace(detail) + "\n\n");
                    count++;
                }
            }
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private static void writeHtmlReport(Diff diff, String outputPath) throws IOException {
        FileWriter writer = null;
        try {
            writer = new FileWriter(outputPath);
 
            writer.write("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
            writer.write("<title>XML Comparison Report</title>");
            writer.write("<style>");
            writer.write("body { font-family: Arial, sans-serif; margin: 20px; }");
            writer.write("table { border-collapse: collapse; width: 100%; margin-top: 20px; }");
            writer.write("th, td { border: 1px solid #ccc; padding: 8px; text-align: left; vertical-align: top; }");
            writer.write("th { background-color: #f2f2f2; }");
            writer.write("tr:nth-child(even) { background-color: #f9f9f9; }");
            writer.write(".diff-header { font-size: 20px; font-weight: bold; margin-bottom: 10px; }");
            writer.write("</style>");
            writer.write("</head><body>");

            writer.write("<div class='diff-header'>Differences Between XML Files</div>");

            if (!diff.hasDifferences()) {
                writer.write("<p>No differences found.</p>");
            } else {
                writer.write("<table>");
                writer.write("<tr><th>#</th><th>Type</th><th>XPath</th><th>Details</th></tr>");
                int count = 1;
                for (Difference difference : diff.getDifferences()) {
                    String comparisonType = escapeHtml(difference.getComparison().getType().toString());

                    String xpath = sanitizeNamespace(difference.getComparison().getControlDetails().getXPath());
                    if (xpath == null || xpath.isEmpty()) {
                        xpath = sanitizeNamespace(difference.getComparison().getTestDetails().getXPath());
                    }

                    String controlValue = safeValue(difference.getComparison().getControlDetails().getValue());
                    String testValue = safeValue(difference.getComparison().getTestDetails().getValue());
                    String detail = "Expected: " + controlValue + ", Found: " + testValue;

                    String description = escapeHtml(sanitizeNamespace(detail));
                    xpath = escapeHtml(xpath);

                    writer.write("<tr>");
                    writer.write("<td>" + count + "</td>");
                    writer.write("<td>" + comparisonType + "</td>");
                    writer.write("<td>" + xpath + "</td>");
                    writer.write("<td>" + description + "</td>");
                    writer.write("</tr>");
                    count++;
                }
                writer.write("</table>");
            }

            writer.write("</body></html>");
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }

    private static String sanitizeNamespace(String input) {
        if (input == null) return "";
        return input.replace("{urn:iso:std:iso:20022:tech:xsd:pain.001.001.03}", "");
    }

    private static String safeValue(Object value) {
        return value == null ? "(null)" : value.toString();
    }
}
