package ReasonCode;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class FailingPain001GeneratorWithReasonCodes {

    private static final List<String> REASON_CODES = Arrays.asList("AC03", "AM01", "BE05", "RC01", "DT01", "DU02");
    private static final String OUTPUT_DIR = "output_Negative/";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmssSSS");
    private static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static void main(String[] args) {
        String templatePath = "//TempleteFile//pain7v2.xml";
        generateFailingPainFiles(templatePath, OUTPUT_DIR, REASON_CODES);
    }

    public static void generateFailingPainFiles(String templatePath, String outputDir, List<String> reasonCodes) {
        try {
            String baseXml = new String(Files.readAllBytes(Paths.get(templatePath)), "UTF-8");
            String detectedVersion = detectPainVersion(baseXml);
            String versionLabel = mapVersionToLabel(detectedVersion);
            System.out.println("Detected PAIN version: " + versionLabel);

            new File(outputDir).mkdirs();

            for (String reason : reasonCodes) {
                String timestamp = DATE_FORMAT.format(new Date());

                if ("DU02".equals(reason)) {
                    String sharedMsgId = "MSG-DU02-" + timestamp;
                    for (int i = 1; i <= 2; i++) {
                        String txnId = "TXN-DU02-Batch" + i + "-" + timestamp;
                        String pmtInfId = "PMTINF-DU02-Batch" + i + "-" + timestamp;
                        String xml = injectInvalidData(baseXml, reason, versionLabel);
                        xml = replaceTag(xml, "MsgId", sharedMsgId);
                        xml = replaceTag(xml, "EndToEndId", txnId);
                        xml = replaceTag(xml, "PmtInfId", pmtInfId);
                        xml = updateCreDtTm(xml);
                        String filename = String.format("InputFile_%s_fail_DU02_batch%d_%s.xml", versionLabel, i, timestamp);
                        Files.write(Paths.get(outputDir + filename), xml.getBytes("UTF-8"));
                    }
                } else {
                    String msgId = "MSG-" + reason + "-" + timestamp;
                    String txnId = "TXN-" + reason + "-" + timestamp;
                    String pmtInfId = "PMTINF-" + reason + "-" + timestamp;
                    String xml = injectInvalidData(baseXml, reason, versionLabel);
                    xml = replaceTag(xml, "MsgId", msgId);
                    xml = replaceTag(xml, "EndToEndId", txnId);
                    xml = replaceTag(xml, "PmtInfId", pmtInfId);
                    xml = updateCreDtTm(xml);
                    String filename = String.format("InputFile_%s_fail_%s_%s.xml", versionLabel, reason, timestamp);
                    Files.write(Paths.get(outputDir + filename), xml.getBytes("UTF-8"));
                }
            }

            System.out.println("All files generated successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String injectInvalidData(String xml, String reason, String versionLabel) {
        try {
            Document doc = loadXmlDocument(xml);
            Map<String, String> attr = new HashMap<String, String>();

            switch (reason) {
                case "AC03":
                    if (versionLabel.startsWith("PAIN1")) {
                        attr.put("Ccy", "XXX");
                        updateNodeByXPath(doc, "/Document/CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/InstdAmt", "100.00", attr);
                    }
                    break;

                case "AM01":
                    if (versionLabel.startsWith("PAIN1")) {
                        updateNodeByXPath(doc, "/Document/CstmrCdtTrfInitn/PmtInf/CtrlSum", "0.00", null);
                        updateNodeByXPath(doc, "/Document/CstmrCdtTrfInitn/PmtInf/NbOfTxs", "1", null);
                        attr.put("Ccy", "EUR");
                        updateNodeByXPath(doc, "/Document/CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/InstdAmt", "0.00", attr);
                    }
                    break;

                case "BE05":
                    if (versionLabel.startsWith("PAIN1")) {
                        updateNodeByXPath(doc, "/Document/CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/MndtRltdInf/MndtId", "XYZINVALID", null);
                    }
                    break;

                case "RC01":
                    if (versionLabel.startsWith("PAIN1")) {
                        updateNodeByXPath(doc, "/Document/CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/CdtrAgt/FinInstnId/BIC", "XXXXBANK99", null);
                    } else if (versionLabel.startsWith("PAIN8")) {
                        updateNodeByXPath(doc, "/Document/CstmrDrctDbtInitn/PmtInf/DrctDbtTxInf/CdtrAgt/FinInstnId/BIC", "XXXXBANK99", null);
                    }
                    break;

                case "DT01":
                    String pastDate = LocalDate.now().minusMonths(2).toString();
                    if (versionLabel.startsWith("PAIN1")) {
                        updateNodeByXPath(doc, "/Document/CstmrCdtTrfInitn/PmtInf/ReqdExctnDt", pastDate, null);
                    } else if (versionLabel.startsWith("PAIN8")) {
                        updateNodeByXPath(doc, "/Document/CstmrDrctDbtInitn/PmtInf/ReqdColltnDt", pastDate, null);
                    }
                    break;

                case "DU02":
                    // No corruption needed for DU02 (duplicate), handled separately.
                    break;

                default:
                    System.out.println("No logic for reason: " + reason);
            }

            return writeXmlDocument(doc);
        } catch (Exception e) {
            e.printStackTrace();
            return xml;
        }
    }

    private static void updateNodeByXPath(Document doc, String xpathExpr, String newValue, Map<String, String> attributes) throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Node node = (Node) xpath.evaluate(xpathExpr, doc, XPathConstants.NODE);
        if (node != null) {
            node.setTextContent(newValue);
            if (attributes != null && node instanceof Element) {
                for (Map.Entry<String, String> entry : attributes.entrySet()) {
                    ((Element) node).setAttribute(entry.getKey(), entry.getValue());
                }
            }
        } else {
            System.out.println("WARNING: XPath not found -> " + xpathExpr);
        }
    }

    private static Document loadXmlDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
    }

    private static String writeXmlDocument(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    private static String updateCreDtTm(String xml) {
        String currentDateTime = LocalDateTime.now().format(ISO_DATE_TIME_FORMATTER);
        return xml.replaceFirst("<CreDtTm>.*?</CreDtTm>", "<CreDtTm>" + currentDateTime + "</CreDtTm>");
    }

    private static String replaceTag(String xml, String tagName, String newValue) {
        return xml.replaceFirst("<" + tagName + ">.*?</" + tagName + ">", "<" + tagName + ">" + newValue + "</" + tagName + ">");
    }

    private static String detectPainVersion(String xml) {
        Pattern pattern = Pattern.compile("xmlns=\\\"urn:iso:std:iso:20022:tech:xsd:(pain\\.\\d{3}\\.\\d{3}\\.\\d{2}|pain\\.[^\\\"]+)\\\"");
        Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) return matcher.group(1);
        return "Unknown";
    }

    private static String mapVersionToLabel(String version) {
        if (version == null) return "UNKNOWN";
        if (version.equals("001.001.03")) return "PAIN1V3";
        if (version.equals("001.001.06")) return "PAIN1V6";
        if (version.equals("001.001.09")) return "PAIN1V9";
        if (version.equals("pain.008.001.02")) return "PAIN8V102";
        if (version.equals("pain7v9")) return "PAIN7V9";
        return version.replace(".", "").toUpperCase();
    }
}
