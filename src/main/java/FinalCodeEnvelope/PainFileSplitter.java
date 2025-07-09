package FinalCodeEnvelope;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;

public class PainFileSplitter {

    public static void main(String[] args) throws Exception {
    	File inputFile = new File("C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\SMSRFT_PAIN1V3.xml");
       // File inputFile = new File("SMSRFT_PAIN1V3.xml");
        PainFileSplitter splitter = new PainFileSplitter();
        splitter.processPainFile(inputFile, "BIC"); // Use "BIC" or "COUNTRY"
    }

    public void processPainFile(File inputFile, String groupingKey) throws Exception {
        Document inputDoc = loadXML(inputFile);

        Element grpHdr = (Element) inputDoc.getElementsByTagNameNS("*", "GrpHdr").item(0);
        String msgId = getElementText(grpHdr, "MsgId");
        String creDtTm = getElementText(grpHdr, "CreDtTm");

        NodeList paymentInfoList = inputDoc.getElementsByTagNameNS("*", "PmtInf");
        Map<String, List<Element>> groupedPayments = groupPaymentsByKey(paymentInfoList, groupingKey);

        Iterator<Map.Entry<String, List<Element>>> it = groupedPayments.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, List<Element>> entry = it.next();
            String key = entry.getKey();
            List<Element> paymentInfos = entry.getValue();
        
            writeGroupedFile(key, paymentInfos, grpHdr, msgId, creDtTm, groupingKey);
        }
    }

    private Document loadXML(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(file);
    }

    private Map<String, List<Element>> groupPaymentsByKey(NodeList pmtInfList, String keyType) {
        Map<String, List<Element>> map = new HashMap<String, List<Element>>();
        for (int i = 0; i < pmtInfList.getLength(); i++) {
            Element pmtInf = (Element) pmtInfList.item(i);
            Element dbtrAgt = (Element) pmtInf.getElementsByTagNameNS("*", "DbtrAgt").item(0);
            String key = "";

            if ("BIC".equalsIgnoreCase(keyType)) {
                key = getElementText(dbtrAgt, "BIC");
            } else if ("COUNTRY".equalsIgnoreCase(keyType)) {
                NodeList pstlAdrList = dbtrAgt.getElementsByTagNameNS("*", "PstlAdr");
                if (pstlAdrList.getLength() > 0) {
                    Element pstlAdr = (Element) pstlAdrList.item(0);
                    key = getElementText(pstlAdr, "Ctry");
                }
            }

            if (key != null && !key.isEmpty()) {
                if (!map.containsKey(key)) {
                    map.put(key, new ArrayList<Element>());
                }
                map.get(key).add(pmtInf);
            }
        }
        return map;
    }

    private void writeGroupedFile(String key, List<Element> pmtInfList, Element originalGrpHdr,
                                  String msgId, String creDtTm, String keyType) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element envelope = doc.createElementNS("http://schemas.xmlsoap.org/soap/envelope/", "env:Envelope");
        doc.appendChild(envelope);

        String receiverBIC = "BIC".equalsIgnoreCase(keyType) ? key : extractBICFromPmtInf(pmtInfList.get(0));
        Element appHdr = createAppHdr(doc, msgId, creDtTm, receiverBIC);
        envelope.appendChild(appHdr);

        Element document = doc.createElement("Document");
        document.setAttribute("xmlns", "urn:iso:std:iso:20022:tech:xsd:pain.001.001.03");
        envelope.appendChild(document);

        Element root = doc.createElement("CstmrCdtTrfInitn");
        root.setAttribute("xmlns", "urn:iso:std:iso:20022:tech:xsd:pain.001.001.03");
        document.appendChild(root);

        Element newGrpHdr = (Element) doc.importNode(originalGrpHdr, true);
        updateGrpHdr(newGrpHdr, pmtInfList);
        root.appendChild(newGrpHdr);

        for (int i = 0; i < pmtInfList.size(); i++) {
            Element pmtInf = pmtInfList.get(i);
            Node cloned = doc.importNode(pmtInf, true);
            root.appendChild(cloned);
        }

        String suffix = "BIC".equalsIgnoreCase(keyType) ? key : "CTRY_" + key;
        removeWhitespaceNodes(doc.getDocumentElement());  //It is used to remove white space
        writeXmlToFile(doc, "Batch_" + suffix + ".txt");
    }

    private String extractBICFromPmtInf(Element pmtInf) {
        Element dbtrAgt = (Element) pmtInf.getElementsByTagNameNS("*", "DbtrAgt").item(0);
        return getElementText(dbtrAgt, "BIC");
    }

    private Element createAppHdr(Document doc, String msgId, String creDtTm, String receiverBIC) {
        Element appHdr = doc.createElementNS("urn:iso:std:iso:20022:tech-xsd:head.001.001.02", "AppHdr");

        Element fr = doc.createElement("Fr");
        Element frField = doc.createElement("FIld");
        Element frFinInstId = doc.createElement("Finlnstnld");
        frFinInstId.appendChild(createTextElement(doc, "BICFI", "SENDERBIC"));
        frField.appendChild(frFinInstId);
        fr.appendChild(frField);
        appHdr.appendChild(fr);

        Element to = doc.createElement("To");
        Element toField = doc.createElement("Flld");
        Element toFinInstId = doc.createElement("Finlnstnld");
        toFinInstId.appendChild(createTextElement(doc, "BICFI", receiverBIC));
        toField.appendChild(toFinInstId);
        to.appendChild(toField);
        appHdr.appendChild(to);

        appHdr.appendChild(createTextElement(doc, "BizMsgldr", msgId));
        appHdr.appendChild(createTextElement(doc, "MsgDefldr", "pacs.001.001.03"));
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
        int totalTxs = 0;

        for (int i = 0; i < pmtInfList.size(); i++) {
            Element pmtInf = pmtInfList.get(i);
            NodeList txList = pmtInf.getElementsByTagNameNS("*", "CdtTrfTxInf");
            totalTxs += txList.getLength();

            for (int j = 0; j < txList.getLength(); j++) {
                Element txInf = (Element) txList.item(j);
                String amtText = getElementText(txInf, "InstdAmt");
                if (amtText != null && !amtText.isEmpty()) {
                    ctrlSum += Double.parseDouble(amtText);
                }
            }
        }

        updateOrCreate(grpHdr, "CtrlSum", String.format("%.2f", ctrlSum));
        updateOrCreate(grpHdr, "NbOfTxs", String.valueOf(totalTxs));
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

    private String getElementText(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagNameNS("*", tagName);
        if (list.getLength() > 0) {
            Node node = list.item(0);
            return node.getTextContent();
        }
        return null;
    }

    private void writeXmlToFile(Document doc, String fileName) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        FileOutputStream fos = new FileOutputStream(fileName);
        transformer.transform(new DOMSource(doc), new StreamResult(fos));
        
        fos.close();
    }  
    
    
    public static void removeWhitespaceNodes(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                // Remove if the text is only whitespace (e.g., spaces, tabs, newlines)
                if (child.getTextContent().trim().isEmpty()) {
                    node.removeChild(child);
                }
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                removeWhitespaceNodes(child); // Recursively clean children
            }
        }
    }
}

