package FinalCode;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.*;

public class Pain001BatchSorter {

    private static final String NS = "urn:iso:std:iso:20022:tech:xsd:pain.001.001.03";

    public static void main(String[] args) throws Exception {
     processFile("C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\SMSRFT_PAIN1V3.xml", "bic");
      
    }

    public static void processFile(String inputFileName, String mode) throws Exception {
        File inputFile = new File(inputFileName);

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document inputDoc = dBuilder.parse(inputFile);

        Element grpHdr = (Element) inputDoc.getElementsByTagNameNS(NS, "GrpHdr").item(0);
        String creationDate = grpHdr.getElementsByTagNameNS(NS, "CreDtTm").item(0).getTextContent();
        String msgId = grpHdr.getElementsByTagNameNS(NS, "MsgId").item(0).getTextContent();

        NodeList pmtInfs = inputDoc.getElementsByTagNameNS(NS, "PmtInf");

        Map<String, List<Element>> groups = new HashMap<String, List<Element>>();

        for (int i = 0; i < pmtInfs.getLength(); i++) {
            Element pmtInf = (Element) pmtInfs.item(i);
            String key;

            if ("bic".equalsIgnoreCase(mode)) {
                key = getDbtrBic(pmtInf);
            } else if ("country".equalsIgnoreCase(mode)) {
                String bic = getDbtrBic(pmtInf);
                key = bic.substring(4, 6); // ISO country code from BIC
            } else {
                throw new IllegalArgumentException("Unsupported grouping mode: " + mode);
            }

            List<Element> list = groups.get(key);
            if (list == null) {
                list = new ArrayList<Element>();
                groups.put(key, list);
            }
            list.add(pmtInf);
        }

        Iterator<Map.Entry<String, List<Element>>> it = groups.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, List<Element>> entry = it.next();
            String groupKey = entry.getKey();
            List<Element> pmtGroup = entry.getValue();

            Document outputDoc = dBuilder.newDocument();

            Element document = outputDoc.createElementNS(NS, "Document");
            outputDoc.appendChild(document);

          //  Element appHdr = createAppHdr(outputDoc, getDbtrBic(pmtGroup.get(0)), getReceiverBic(pmtGroup), creationDate, msgId);
          //  outputDoc.appendChild(appHdr);
            
            Element appHdr = createAppHdr(outputDoc, getDbtrBic(pmtGroup.get(0)), getReceiverBic(pmtGroup), creationDate, msgId);
            document.appendChild(appHdr);

            Element cstmrCdtTrfInitn = outputDoc.createElementNS(NS, "CstmrCdtTrfInitn");
            document.appendChild(cstmrCdtTrfInitn);

            Node copiedGrpHdr = outputDoc.importNode(grpHdr, true);
            updateGrpHdrTotals(outputDoc, copiedGrpHdr, pmtGroup);
            cstmrCdtTrfInitn.appendChild(copiedGrpHdr);

            for (int i = 0; i < pmtGroup.size(); i++) {
                Node copied = outputDoc.importNode(pmtGroup.get(i), true);
                cstmrCdtTrfInitn.appendChild(copied);
            }

            String fileName = "output_" + mode + "_" + groupKey + ".txt";
            writeXmlToFile(outputDoc, fileName);
            System.out.println("Written: " + fileName);
        }
    }

    private static String getDbtrBic(Element pmtInf) {
        return pmtInf.getElementsByTagNameNS(NS, "BIC").item(0).getTextContent();
    }

    private static String getReceiverBic(List<Element> pmtInfs) {
        for (int i = 0; i < pmtInfs.size(); i++) {
            Element pmtInf = pmtInfs.get(i);
            NodeList txList = pmtInf.getElementsByTagNameNS(NS, "CdtTrfTxInf");
            if (txList.getLength() > 0) {
                Element tx = (Element) txList.item(0);
                return tx.getElementsByTagNameNS(NS, "BIC").item(0).getTextContent();
            }
        }
        return "UNKNOWN";
    }

    private static Element createAppHdr(Document doc, String senderBic, String receiverBic, String creationDate, String msgId) {
        Element appHdr = doc.createElementNS("urn:iso:std:iso:20022:tech:xsd:head.001.001.02", "AppHdr");

        Element fr = doc.createElement("Fr");
        Element frFld = doc.createElement("FIld");
        Element frInst = doc.createElement("Finlnstnld");
        Element frBic = doc.createElement("BICFI");
        frBic.setTextContent(senderBic);
        frInst.appendChild(frBic);
        frFld.appendChild(frInst);
        fr.appendChild(frFld);
        appHdr.appendChild(fr);

        Element to = doc.createElement("To");
        Element toFld = doc.createElement("Flld");
        Element toInst = doc.createElement("Finlnstnld");
        Element toBic = doc.createElement("BICFI");
        toBic.setTextContent(receiverBic);
        toInst.appendChild(toBic);
        toFld.appendChild(toInst);
        to.appendChild(toFld);
        appHdr.appendChild(to);

        appendTextElement(doc, appHdr, "BizMsgldr", msgId);
        appendTextElement(doc, appHdr, "MsgDefldr", "pacs.001.001.03");
        appendTextElement(doc, appHdr, "BizSvc", "swift.cbprplus.02");
        appendTextElement(doc, appHdr, "CreDt", creationDate);

        return appHdr;
    }

    private static void appendTextElement(Document doc, Element parent, String name, String value) {
        Element el = doc.createElement(name);
        el.setTextContent(value);
        parent.appendChild(el);
    }

    private static void updateGrpHdrTotals(Document doc, Node grpHdr, List<Element> pmtGroup) {
        int totalTxs = 0;
        double totalSum = 0.0;

        for (int i = 0; i < pmtGroup.size(); i++) {
            Element pmtInf = pmtGroup.get(i);
            NodeList txList = pmtInf.getElementsByTagNameNS(NS, "CdtTrfTxInf");
            totalTxs += txList.getLength();
            for (int j = 0; j < txList.getLength(); j++) {
                Element tx = (Element) txList.item(j);
                String amtStr = tx.getElementsByTagNameNS(NS, "InstdAmt").item(0).getTextContent();
                totalSum += Double.parseDouble(amtStr);
            }
        }

        updateElement(grpHdr, "NbOfTxs", String.valueOf(totalTxs));
        updateElement(grpHdr, "CtrlSum", String.format("%.2f", totalSum));
    }

    private static void updateElement(Node parent, String tagName, String newValue) {
        NodeList list = ((Element) parent).getElementsByTagNameNS(NS, tagName);
        if (list.getLength() > 0) {
            list.item(0).setTextContent(newValue);
        }
    }

    private static void writeXmlToFile(Document doc, String filename) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(filename));
        transformer.transform(source, result);
    }
}
