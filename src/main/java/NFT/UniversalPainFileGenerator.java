package NFT;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class UniversalPainFileGenerator {

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter path to PAIN template XML:");
        String templatePath = scanner.nextLine();

        System.out.println("Enter total number of transactions:");
        int totalTransactions = scanner.nextInt();

        System.out.println("Enter number of batches:");
        int batchCount = scanner.nextInt();

        Map<Integer, Integer> batchMap = distributeTransactions(totalTransactions, batchCount);

        Document templateDoc = loadTemplate(templatePath);
        String ns = templateDoc.getDocumentElement().getNamespaceURI();

        Node rootElement = templateDoc.getDocumentElement();
        NodeList children = rootElement.getChildNodes();
        String coreElementName = null;
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                coreElementName = children.item(i).getLocalName();
                break; 
            }
        }
        if (coreElementName == null) throw new RuntimeException("No valid core message found.");

        String txnTag = detectTxnTag(coreElementName);
        String txnAmtPath = detectAmtPath(coreElementName);

        Document newDoc = createDocument();
        Element root = newDoc.createElementNS(ns, templateDoc.getDocumentElement().getLocalName());
        newDoc.appendChild(root);

        Node appHdr = getNode(templateDoc, "AppHdr");
        if (appHdr != null) root.appendChild(newDoc.importNode(appHdr, true));

        Element coreBlock = (Element) getNode(templateDoc, coreElementName);
        Element newCoreBlock = newDoc.createElementNS(ns, coreElementName);
        root.appendChild(newCoreBlock);

        Element grpHdr = (Element) getNode(templateDoc, "GrpHdr");
        Element newGrpHdr = (Element) newDoc.importNode(grpHdr, true);

        DateTimeFormatter msgIdFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
        String msgId = "MSG-" + LocalDateTime.now().format(msgIdFormatter);

        updateText(newGrpHdr, ns, "MsgId", msgId);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        updateText(newGrpHdr, ns, "CreDtTm", OffsetDateTime.now().format(formatter));
        newCoreBlock.appendChild(newGrpHdr);

        int totalTxns = 0;
        double totalSum = 0.0;

        Element txnTemplate = (Element) getNode(templateDoc, txnTag);
        Element pmtTemplate = (Element) getNode(templateDoc, "PmtInf");
        if (pmtTemplate == null) pmtTemplate = (Element) getNode(templateDoc, "OrgnlPmtInfAndRvsl");

        for (int i = 1; i <= batchCount; i++) {
            int txnCount = batchMap.get(i);
            Element newBatch = (Element) newDoc.importNode(pmtTemplate, true);
            clearChildNodes(newBatch, txnTag);

            String batchId = msgId + "-B" + i;

            if ("CstmrPmtRvsl".equals(coreElementName)) {
                // For pain.007, update RvslPmtInfId
                NodeList revIdList = newBatch.getElementsByTagNameNS(ns, "RvslPmtInfId");
                if (revIdList.getLength() > 0) {
                    revIdList.item(0).setTextContent(batchId);
                } else {
                    System.out.println("Warning: <RvslPmtInfId> not found for batch " + i);
                }
            } else {
                updateText(newBatch, ns, "PmtInfId", batchId);
            }

            updateText(newBatch, ns, "ReqdExctnDt", LocalDate.now().toString());

            double batchSum = 0.0;
            for (int j = 0; j < txnCount; j++) {
                Element newTxn = (Element) newDoc.importNode(txnTemplate, true);
                String txnId = batchId + "-T" + (j + 1);
                updateText(newTxn, ns, "EndToEndId", txnId);

                Element amtEl = findElementByPath(newTxn, txnAmtPath);
                double amt = amtEl != null ? Double.parseDouble(amtEl.getTextContent()) : 0.0;
                batchSum += amt;
                
                if (amtEl != null) {
                    Element eqvtAmt = newDoc.createElementNS(ns, "EqvtAmt");
                    eqvtAmt.setTextContent(String.format("%.2f", amt));
                    eqvtAmt.setAttribute("Ccy", amtEl.getAttribute("Ccy"));
                    amtEl.getParentNode().appendChild(eqvtAmt);
                }

                newBatch.appendChild(newTxn);
            }

            updateText(newBatch, ns, "NbOfTxs", String.valueOf(txnCount));
            updateText(newBatch, ns, "CtrlSum", String.format("%.2f", batchSum));
            newCoreBlock.appendChild(newBatch);

            totalTxns += txnCount;
            totalSum += batchSum;
        }

        updateText(newGrpHdr, ns, "NbOfTxs", String.valueOf(totalTxns));
        updateText(newGrpHdr, ns, "CtrlSum", String.format("%.2f", totalSum));

        saveXML(newDoc, "universal-output.xml");
        System.out.println("Generated: universal-output.xml");
    }
    private static Map<Integer, Integer> distributeTransactions(int total, int batches) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        int base = total / batches, rem = total % batches;
        for (int i = 1; i <= batches; i++) map.put(i, base + (i <= rem ? 1 : 0));
        return map;
    }

    private static String detectTxnTag(String rootType) {
        switch (rootType) {
            case "CstmrCdtTrfInitn": return "CdtTrfTxInf";
            case "CstmrDrctDbtInitn": return "DrctDbtTxInf";
            case "CstmrPmtRvsl": return "TxInf";
            default: return "CdtTrfTxInf";
        }
    }

    private static String detectAmtPath(String rootType) {
        switch (rootType) {
            case "CstmrCdtTrfInitn": return "Amt/InstdAmt";
            case "CstmrDrctDbtInitn": return "InstdAmt";
            case "CstmrPmtRvsl": return "RvsdInstdAmt";
            default: return "Amt/InstdAmt";
        }
    }

    private static void updateText(Element parent, String ns, String tag, String value) {
        NodeList nodes = parent.getElementsByTagNameNS(ns, tag);
        if (nodes.getLength() > 0) nodes.item(0).setTextContent(value);
    }

    private static void clearChildNodes(Element parent, String childTag) {
        NodeList list = parent.getElementsByTagNameNS("*", childTag);
        while (list.getLength() > 0) parent.removeChild(list.item(0));
    }

    private static Element findElementByPath(Element base, String path) {
        String[] tags = path.split("/");
        Element current = base;
        for (String tag : tags) {
            NodeList list = current.getElementsByTagNameNS("*", tag);
            if (list.getLength() == 0) return null;
            current = (Element) list.item(0);
        }
        return current;
    }

    private static Document loadTemplate(String path) throws Exception {
        File file = new File(path);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder().parse(file);
    }

    private static Document createDocument() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    private static Node getNode(Document doc, String localName) {
        NodeList all = doc.getElementsByTagNameNS("*", localName);
        return all.getLength() > 0 ? all.item(0) : null;
    }

    private static void saveXML(Document doc, String path) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer tr = tf.newTransformer();
        tr.setOutputProperty(OutputKeys.INDENT, "yes");
        tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        doc.normalizeDocument();
        removeWhitespaceNodes(doc.getDocumentElement());
        DOMSource src = new DOMSource(doc);
        StreamResult out = new StreamResult(new File(path));
        tr.transform(src, out);
    }
    
    private static void removeWhitespaceNodes(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().trim().isEmpty()) {
                element.removeChild(child);
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                removeWhitespaceNodes((Element) child);
            }
        }
    }

}
