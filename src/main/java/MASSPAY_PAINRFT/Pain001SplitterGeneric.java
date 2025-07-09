package MASSPAY_PAINRFT;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class Pain001SplitterGeneric {

    public static void main(String[] args) throws Exception {
        File inputFile = new File("C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\RFT_PAIN1V9.xml");

        String groupType = "BIC"; // or "COUNTRY"
        String groupValue = "BARCGB22XXX";
        String targetType = "MT101"; // PCIRSEPA, NACHA, or MT101

        new Pain001SplitterGeneric().splitFileByGrouping(inputFile, groupType, groupValue, targetType);
    }

    public void splitFileByGrouping(File inputFile, String groupType, String groupValue, String targetType) throws Exception {
        Document inputDoc = loadXML(inputFile);

        Element root = inputDoc.getDocumentElement();
        String version = root.getNamespaceURI().contains("001.001.09") ? "pain.001.001.09" : "pain.001.001.03";

        Element grpHdr = (Element) inputDoc.getElementsByTagNameNS("*", "GrpHdr").item(0);
        String originalMsgId = getElementText(grpHdr, "MsgId");
        String creDtTm = getElementText(grpHdr, "CreDtTm");

        NodeList pmtInfos = inputDoc.getElementsByTagNameNS("*", "PmtInf");
        List<Element> matchedPmtInfList = new ArrayList<Element> ();

        for (int i = 0; i < pmtInfos.getLength(); i++) {
            Element pmtInf = (Element) pmtInfos.item(i);
            Element dbtrAgt = (Element) pmtInf.getElementsByTagNameNS("*", "DbtrAgt").item(0);

            String key = null;
            if ("BIC".equalsIgnoreCase(groupType)) {
                key = getElementText(dbtrAgt, version.endsWith("09") ? "BICFI" : "BIC");
            } else if ("COUNTRY".equalsIgnoreCase(groupType)) {
                NodeList pstlAdrList = dbtrAgt.getElementsByTagNameNS("*", "PstlAdr");
                if (pstlAdrList.getLength() > 0) {
                    Element pstlAdr = (Element) pstlAdrList.item(0);
                    key = getElementText(pstlAdr, "Ctry");
                }
            }

            if (groupValue.equalsIgnoreCase(key)) {
                matchedPmtInfList.add(pmtInf);
            }
        }

        if (!matchedPmtInfList.isEmpty()) {
            switch (targetType.toUpperCase()) {
                case "PCIRSEPA":
                    writePCIRSEPAFile(groupType, groupValue, matchedPmtInfList, grpHdr, originalMsgId, version);
                    break;
                case "NACHA":
                    writeNACHAFile(originalMsgId, groupValue);
                    break;
                case "MT101":
                    writeMT101File(groupValue, matchedPmtInfList, version);
                    break;
                default:
                    System.out.println("Unsupported target type: " + targetType);
            }
        } else {
            System.out.println("No batches found for the provided " + groupType + " = " + groupValue);
        }
    }

    private void writePCIRSEPAFile(String groupType, String keyValue, List<Element> pmtInfList,
                                   Element originalGrpHdr, String msgId, String version) throws Exception {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element root = doc.createElementNS("urn:s2sCTScf:xsd:$pain.001.001.03", "S2SCTScf:SCTScfBlkCredTrf");
        root.setAttribute("xmlns:s2sCTScf", "urn:s2sCTScf:xsd:$pain.001.001.03");
        root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        root.setAttribute("xsi:schemaLocation", "urn:s2sCTScf:xsd:$pain.001.001.03 s2SCTScf.xsd");
        doc.appendChild(root);

        String fileRef = msgId + "_" + keyValue + "_" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());

        root.appendChild(createTextElement(doc, "S2SCTScf:SndgInst", extractBICFromPmtInf(pmtInfList.get(0), version)));
        root.appendChild(createTextElement(doc, "S2SCTScf:RcvgInst", keyValue));
        root.appendChild(createTextElement(doc, "S2SCTScf:FileRef", fileRef));
        root.appendChild(createTextElement(doc, "S2SCTScf:SrvcId", "SCT"));
        root.appendChild(createTextElement(doc, "S2SCTScf:TstCode", "T"));
        root.appendChild(createTextElement(doc, "S2SCTScf:FType", "CISF"));
        root.appendChild(createTextElement(doc, "S2SCTScf:FDtTm", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date())));
        root.appendChild(createTextElement(doc, "S2SCTScf:NumCTBlk", "1"));

        Element ccti = doc.createElementNS("urn:iso:std:iso:20022:tech:xsd:pain.001.001.03", "CstmrCdtTrfInitn");
        root.appendChild(ccti);

        Element newGrpHdr = (Element) doc.importNode(originalGrpHdr, true);
        updateGrpHdr(newGrpHdr, pmtInfList);
        updateOrCreate(newGrpHdr, "MsgId", fileRef);
        ccti.appendChild(newGrpHdr);

        for (Element pmtInf : pmtInfList) {
            Node cloned = doc.importNode(pmtInf, true);
            ccti.appendChild(cloned);
        }

        removeWhitespaceNodes(doc.getDocumentElement());

        String outputFile = "Output_PCIRSEPA_" + keyValue + ".txt";
        writeXmlToFile(doc, outputFile);
        System.out.println("Written PCIRSEPA file: " + outputFile);
    }

    private void writeNACHAFile(String msgId, String groupValue) throws Exception {
        String appHdr = "<AppHdr><Fr>MassPay</Fr><To>" + groupValue + "</To><BizMsgIdr>" +
                msgId + "</BizMsgIdr></AppHdr>";
        try (PrintWriter writer = new PrintWriter("Output_NACHA_" + groupValue + ".txt")) {
            writer.println(appHdr);
        }
        System.out.println("Written NACHA AppHdr only file.");
    }

    private void writeMT101File(String groupValue, List<Element> pmtInfList, String version) throws Exception {
        String filename = "Output_MT101_" + groupValue + ".txt";
        try (PrintWriter writer = new PrintWriter(filename)) {
            for (Element pmtInf : pmtInfList) {
                NodeList txList = pmtInf.getElementsByTagNameNS("*", "CdtTrfTxInf");
                for (int j = 0; j < txList.getLength(); j++) {
                    Element tx = (Element) txList.item(j);

                    String instrId = getElementText(tx, "InstrId");
                    String endToEndId = getElementText(tx, "EndToEndId");
                    String amount = getElementText(tx, "InstdAmt");
                    String currency = tx.getElementsByTagNameNS("*", "InstdAmt").item(0).getAttributes()
                            .getNamedItem("Ccy").getTextContent();

                    String creditorName = getElementText(tx, "Nm");
                    String iban = getElementText(tx, "IBAN");
                    String bic = getElementText(tx, version.endsWith("09") ? "BICFI" : "BIC");
                    String remittanceInfo = getElementText(tx, "Ustrd");
                    String chargeBearer = getElementText(pmtInf, "ChrgBr");

                    writer.println(":20:" + instrId);
                    writer.println(":21:" + endToEndId);
                    writer.println(":32B:" + currency + amount);
                    writer.println(":50H:/IBAN");
                    writer.println(":59:/" + iban);
                    writer.println(creditorName);
                    writer.println(":70:" + (remittanceInfo != null ? remittanceInfo : ""));
                    if (chargeBearer != null)
                        writer.println(":71A:" + chargeBearer);
                    writer.println("-");
                }
            }
        }
        System.out.println("Written MT101 file: " + filename);
    }

    private Element createTextElement(Document doc, String name, String value) {
        Element elem = doc.createElement(name);
        elem.setTextContent(value);
        return elem;
    }

    private void updateGrpHdr(Element grpHdr, List<Element> pmtInfList) {
        double ctrlSum = 0.0;
        int txCount = 0;

        for (Element pmtInf : pmtInfList) {
            NodeList txList = pmtInf.getElementsByTagNameNS("*", "CdtTrfTxInf");
            txCount += txList.getLength();

            for (int j = 0; j < txList.getLength(); j++) {
                Element txInf = (Element) txList.item(j);
                String amtText = getElementText(txInf, "InstdAmt");
                if (amtText != null && !amtText.isEmpty()) {
                    ctrlSum += Double.parseDouble(amtText);
                }
            }
        }

        updateOrCreate(grpHdr, "CtrlSum", String.format("%.2f", ctrlSum));
        updateOrCreate(grpHdr, "NbOfTxs", String.valueOf(txCount));
    }

    private void updateOrCreate(Element parent, String tagName, String newValue) {
        NodeList nodes = parent.getElementsByTagNameNS("*", tagName);
        if (nodes.getLength() > 0) {
            nodes.item(0).setTextContent(newValue);
        } else {
            Element newElem = parent.getOwnerDocument().createElement(tagName);
            newElem.setTextContent(newValue);
            parent.appendChild(newElem);
        }
    }

    private String extractBICFromPmtInf(Element pmtInf, String version) {
        Element dbtrAgt = (Element) pmtInf.getElementsByTagNameNS("*", "DbtrAgt").item(0);
        return getElementText(dbtrAgt, version.endsWith("09") ? "BICFI" : "BIC");
    }

    private String getElementText(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagNameNS("*", tagName);
        if (list.getLength() > 0) {
            return list.item(0).getTextContent();
        }
        return null;
    }

    private Document loadXML(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(file);
    }

    private void writeXmlToFile(Document doc, String fileName) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            transformer.transform(new DOMSource(doc), new StreamResult(fos));
        }
    }

    public static void removeWhitespaceNodes(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().trim().isEmpty()) {
                node.removeChild(child);
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                removeWhitespaceNodes(child);
            }
        }
    }
}
