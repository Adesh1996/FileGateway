package ReasonCode;



import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PSRPain001GeneratorWithReasonCodes {

    private static final List<String> REASON_CODES = Arrays.asList("AC03", "AM01", "BE05", "RC01", "DT01", "DU02");
    private static final String OUTPUT_DIR = "output_Negative/";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmssSSS");
    private static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static void main(String[] args) {
    	 String templatePath = "F://Barclays BFG Project//FileGateway//TempleteFile//RFT_PAIN1V9.xml";
    	    System.out.println("Template Path: " + templatePath);
    	    System.out.println("Output Dir: " + OUTPUT_DIR);
    	    System.out.println("Reason Codes: " + REASON_CODES);
         generateFailingPainFiles(templatePath, OUTPUT_DIR, REASON_CODES);
    }

    public static void generateFailingPainFiles(String templatePath, String outputDir, List<String> reasonCodes) {
        try {
        	
        	  File templateFile = new File(templatePath);
              if (!templateFile.exists()) {
                  throw new FileNotFoundException("Template XML file not found at: " + templatePath);
              }
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document baseDoc = dBuilder.parse(new File(templatePath));
            baseDoc.getDocumentElement().normalize();

            String baseXml = new String(Files.readAllBytes(Paths.get(templatePath)), "UTF-8");
            String detectedVersion = detectPainVersion(baseXml);
            System.out.println("Detected PAIN version: " + detectedVersion);

            new File(outputDir).mkdirs();

            for (String reason : reasonCodes) {
                String timestamp = DATE_FORMAT.format(new Date()); 
                String versionLabel = mapVersionToLabel(detectedVersion);

                if ("DU02".equals(reason)) {
                    String sharedMsgId = "MSG-DU02-" + timestamp;
                    for (int i = 1; i <= 2; i++) {
                        Document doc = (Document) baseDoc.cloneNode(true);
                        injectInvalidData(doc, reason);
                        updateValue(doc, "MsgId", sharedMsgId);
                        updateValue(doc, "EndToEndId", "TXN-DU02-Batch" + i + "-" + timestamp);
                        updateValue(doc, "PmtInfId", "PMTINF-DU02-Batch" + i + "-" + timestamp);
                        updateCreDtTm(doc);

                        String filename = String.format("InputFile_%s_fail_DU02_batch%d_%s.xml", versionLabel, i, timestamp);
                        writeXmlToFile(doc, outputDir + filename);
                    }
                } else {
                    Document doc = (Document) baseDoc.cloneNode(true);
                    injectInvalidData(doc, reason);
                    updateValue(doc, "MsgId", "MSG-" + reason + "-" + timestamp);
                    updateValue(doc, "EndToEndId", "TXN-" + reason + "-" + timestamp);
                    updateValue(doc, "PmtInfId", "PMTINF-" + reason + "-" + timestamp);
                    updateCreDtTm(doc);

                    String filename = String.format("InputFile_%s_fail_%s_%s.xml", versionLabel, reason, timestamp);
                    writeXmlToFile(doc, outputDir + filename);
                }
            }

            System.out.println("All files generated successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void injectInvalidData(Document doc, String reason) {
        switch (reason) {
            case "AC03":
                updateCurrency(doc, "InstdAmt", "XXX");
                break;
            case "AM01":
                updateValue(doc, "InstdAmt", "0.00");
                updateAttribute(doc, "InstdAmt", "Ccy", "EUR");
                updateValue(doc, "CtrlSum", "0.00");
                updateValue(doc, "NbOfTxs", "1");
                break;
            case "BE05":
                updateValue(doc, "MndtId", "XYZINVALID");
                break;
            case "RC01":
                updateValue(doc, "BIC", "XXXXBANK99");
                break;
            case "DT01":
                updateValue(doc, "ReqdExctnDt", LocalDate.now().minusMonths(2).toString());
                break;
            case "DU02":
                // No change
                break;
            default:
                break;
        }
    }

    private static void updateValue(Document doc, String tag, String newValue) {
        NodeList nodeList = doc.getElementsByTagName(tag);
        if (nodeList.getLength() > 0) {
            nodeList.item(0).setTextContent(newValue);
        }
    }

    private static void updateAttribute(Document doc, String tag, String attrName, String attrValue) {
        NodeList nodeList = doc.getElementsByTagName(tag);
        if (nodeList.getLength() > 0) {
            Element element = (Element) nodeList.item(0);
            element.setAttribute(attrName, attrValue);
        }
    }

    private static void updateCurrency(Document doc, String tag, String currency) {
        NodeList nodeList = doc.getElementsByTagName(tag);
        for (int i = 0; i < nodeList.getLength(); i++) {
            Element amt = (Element) nodeList.item(i);
            amt.setAttribute("Ccy", currency);
        }
    }

    private static void updateCreDtTm(Document doc) {
        String currentDateTime = LocalDateTime.now().format(ISO_DATE_TIME_FORMATTER);
        updateValue(doc, "CreDtTm", currentDateTime);
    }

    private static String mapVersionToLabel12(String namespace) {
        switch (namespace) {
            case "urn:iso:std:iso:20022:tech:xsd:pain.001.001.03": return "PAIN1V3";
            case "urn:iso:std:iso:20022:tech:xsd:pain.001.001.06": return "PAIN1V6";
            case "urn:iso:std:iso:20022:tech:xsd:pain.001.001.09": return "PAIN1V9";
            case "urn:iso:std:iso:20022:tech:xsd:pain.007.001.02": return "PAIN7V2";
            case "urn:iso:std:iso:20022:tech:xsd:pain8v8": return "PAIN8V8";
            case "urn:iso:std:iso:20022:tech:xsd:payext": return "PAYEXT";
            case "urn:iso:std:iso:20022:tech:xsd:paymul": return "PAYMUL";
            default: return namespace.replaceAll("[^a-zA-Z0-9]", "");
        }
    }
    
    private static String mapVersionToLabel(String version) {
        switch (version) {
            case "001.001.03": return "PAIN1V3";
            case "001.001.06": return "PAIN1V6"; 
            case "001.001.09": return "PAIN1V9";
            case "pain.001.001": return "PAIN1";
            case "pain.001.002": return "PAIN2";
            case "pain.001.003": return "PAIN3";
            case "pain.008.001": return "PAIN8V1";
            case "pain.008.002": return "PAIN8V2";
            case "pain.008.003": return "PAIN8V3";
            case "pain.008.001.02": return "PAIN8V102";
            case "pain7v9": return "PAIN7V9";
            case "pain8v8": return "PAIN8V8";
            case "payext": return "PAYEXT";
            case "paymul": return "PAYMUL";
            default: return version.replace(".", "");
        }
    }

    private static void writeXmlToFile(Document doc, String path) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(path));
            transformer.transform(source, result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static String detectPainVersion(String xml) {
        Pattern pattern = Pattern.compile("xmlns=\\\"urn:iso:std:iso:20022:tech:xsd:(pain\\.\\d{3}\\.\\d{3}\\.\\d{2}|pain\\.\\d{3}\\.\\d{3}|pain7v9|pain8v8|payext|paymul)\\\"");
        Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "Unknown";
    }    
}
