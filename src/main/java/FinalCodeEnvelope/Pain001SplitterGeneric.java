package FinalCodeEnvelope;


import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.*;

public class Pain001SplitterGeneric {

    public static void main(String[] args) throws Exception {
        File inputFile = new File("C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\RFT_PAIN1V9.xml");

        String groupType = "BIC"; // or "COUNTRY"
        String groupValue = "BARCGB22XXX";

        new Pain001SplitterGeneric().splitFileByGrouping(inputFile, groupType, groupValue);
    }

    public void splitFileByGrouping(File inputFile, String groupType, String groupValue) throws Exception {
        Document inputDoc = loadXML(inputFile);

        Element root = inputDoc.getDocumentElement();
        String version = root.getNamespaceURI().endsWith("pain.001.001.09") ? "pain.001.001.09" : "pain.001.001.03";

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
                key = getElementText(dbtrAgt, version.equals("pain.001.001.09") ? "BICFI" : "BIC");
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
            writeOutputFile(groupType, groupValue, matchedPmtInfList, grpHdr, originalMsgId, creDtTm, version);
        } else {
            System.out.println("No batches found for the provided " + groupType + " = " + groupValue);
        }
    }

    private void writeOutputFile(String groupType, String keyValue, List<Element> pmtInfList,
                                 Element originalGrpHdr, String msgId, String creDtTm, String version) throws Exception {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element envelope = doc.createElementNS("http://schemas.xmlsoap.org/soap/envelope/", "env:Envelope");
        doc.appendChild(envelope);

        String receiverBIC = "BIC".equalsIgnoreCase(groupType) ? keyValue : extractBICFromPmtInf(pmtInfList.get(0), version);
        String newMsgId = msgId + "_" + groupType + "_" + keyValue.replaceAll("[^a-zA-Z0-9]", "_");

        Element appHdr = createAppHdr(doc, newMsgId, creDtTm, receiverBIC, version);
        envelope.appendChild(appHdr);

        Element document = doc.createElement("Document");
        document.setAttribute("xmlns", "urn:iso:std:iso:20022:tech:xsd:" + version);
        envelope.appendChild(document);

        Element root = doc.createElement("CstmrCdtTrfInitn");
        document.appendChild(root);

        Element newGrpHdr = (Element) doc.importNode(originalGrpHdr, true);
        updateGrpHdr(newGrpHdr, pmtInfList);
        updateOrCreate(newGrpHdr, "MsgId", newMsgId);
        root.appendChild(newGrpHdr);

        for (Element pmtInf : pmtInfList) {
            Node cloned = doc.importNode(pmtInf, true);
            root.appendChild(cloned);
        }

        removeWhitespaceNodes(doc.getDocumentElement());

        String outputFile = "Output_" + groupType + "_" + keyValue.replaceAll("[^a-zA-Z0-9]", "_") + ".txt";
        writeXmlToFile(doc, outputFile);
        System.out.println("Written file: " + outputFile);
    }

    private Element createAppHdr(Document doc, String msgId, String creDtTm, String receiverBIC, String version) {
        Element appHdr = doc.createElementNS("urn:iso:std:iso:20022:tech:xsd:head.001.001.02", "AppHdr");

        Element fr = doc.createElement("Fr");
        Element frFIId = doc.createElement("FIId");
        Element frFinInstId = doc.createElement("FinInstnId");
        frFinInstId.appendChild(createTextElement(doc, "BICFI", "SENDERBIC"));
        frFIId.appendChild(frFinInstId);
        fr.appendChild(frFIId);
        appHdr.appendChild(fr);

        Element to = doc.createElement("To");
        Element toFIId = doc.createElement("FIId");
        Element toFinInstId = doc.createElement("FinInstnId");
        toFinInstId.appendChild(createTextElement(doc, "BICFI", receiverBIC));
        toFIId.appendChild(toFinInstId);
        to.appendChild(toFIId);
        appHdr.appendChild(to);

        appHdr.appendChild(createTextElement(doc, "BizMsgIdr", msgId));
        appHdr.appendChild(createTextElement(doc, "MsgDefIdr", version)); // dynamic
        appHdr.appendChild(createTextElement(doc, "BizSvc", "swift.cbprplus.02"));
        appHdr.appendChild(createTextElement(doc, "CreDt", creDtTm));

        return appHdr;
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
        return getElementText(dbtrAgt, version.equals("pain.001.001.09") ? "BICFI" : "BIC");
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
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
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
