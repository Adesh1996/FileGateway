import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.*;

public class Pain001ProcessorFinal {

    private static final String NS = "urn:iso:std:iso:20022:tech:xsd:pain.001.001.03";

    public static void main(String[] args) throws Exception {
    	File inputFile = new File("C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\SMSRFT_PAIN1V3.xml");
        //File inputFile = new File("SMSRFT_PAIN1V3.xml");

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document inputDoc = dBuilder.parse(inputFile);

        Element grpHdr = (Element) inputDoc.getElementsByTagNameNS(NS, "GrpHdr").item(0);
        NodeList pmtInfs = inputDoc.getElementsByTagNameNS(NS, "PmtInf");

        // Extract CreDt from GrpHdr
        String creationDate = grpHdr.getElementsByTagNameNS(NS, "CreDtTm").item(0).getTextContent();

        // Grouping by DbtrAgt BIC
        Map<String, List<Element>> bicGroups = new HashMap<String, List<Element>>();
        for (int i = 0; i < pmtInfs.getLength(); i++) {
            Element pmtInf = (Element) pmtInfs.item(i);
            String bic = pmtInf.getElementsByTagNameNS(NS, "BIC").item(0).getTextContent();

            List<Element> list = bicGroups.get(bic);
            if (list == null) {
                list = new ArrayList<Element>();
                bicGroups.put(bic, list);
            }
            list.add(pmtInf);
        }

        Iterator<Map.Entry<String, List<Element>>> it = bicGroups.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, List<Element>> entry = it.next();
            String dbtrBic = entry.getKey();
            List<Element> pmtGroup = entry.getValue();

            Document outputDoc = dBuilder.newDocument();

            Element envelope = outputDoc.createElementNS("urn:swift;xsd:envelope", "env:Envelope");
            envelope.setAttribute("xmlns:env", "urn:swift;xsd:envelope");
            envelope.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
            outputDoc.appendChild(envelope);

            String receiverBic = findReceiverBic(pmtGroup);
            Element appHdr = createAppHdr(outputDoc, dbtrBic, receiverBic, creationDate);
            envelope.appendChild(appHdr);

            Element document = outputDoc.createElementNS(NS, "Document");
            envelope.appendChild(document);

            Element cstmrCdtTrfInitn = outputDoc.createElementNS(NS, "CstmrCdtTrfInitn");
            document.appendChild(cstmrCdtTrfInitn);

            // Copy <GrpHdr> and update totals
            Node copiedGrpHdr = outputDoc.importNode(grpHdr, true);
            updateGrpHdrTotals(outputDoc, copiedGrpHdr, pmtGroup);
            cstmrCdtTrfInitn.appendChild(copiedGrpHdr);

            // Append matching PmtInf entries
            for (int i = 0; i < pmtGroup.size(); i++) {
                Element pmtInf = pmtGroup.get(i);
                Node copied = outputDoc.importNode(pmtInf, true);
                cstmrCdtTrfInitn.appendChild(copied);
            }

            String filename = "output_" + dbtrBic + ".txt";
            writeXmlToFile(outputDoc, filename);
            System.out.println("Written: " + filename);
        }
    }

    private static Element createAppHdr(Document doc, String senderBic, String receiverBic, String creationDate) {
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

        appendTextElement(doc, appHdr, "BizMsgldr", "2502271510350696");
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

    private static String findReceiverBic(List<Element> pmtInfs) {
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
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(filename));
        transformer.transform(source, result);
    }
}
