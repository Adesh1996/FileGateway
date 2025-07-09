package FinalCode;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.*;

public class Pain001SplitterUtil {
	
	public static void main(String[] args) throws Exception {

        File inputFile = new File("C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\SMSRFT_PAIN1V3.xml");
        processPainFile(inputFile);
	}
	

    public static void processPainFile(File inputFile) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(inputFile);
        doc.getDocumentElement().normalize();

        Node grpHdr = doc.getElementsByTagNameNS("*", "GrpHdr").item(0);
        NodeList pmtInfs = doc.getElementsByTagNameNS("*", "PmtInf");

        Map<String, List<Element>> groupedMap = groupByFallback(doc, pmtInfs,
            new String[][] {
                {"DbtrAgt", "FinInstnId", "BIC"},
                {"DbtrAgt", "FinInstnId", "Ctry"}
            });

        Iterator<Map.Entry<String, List<Element>>> iterator = groupedMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, List<Element>> entry = iterator.next();
            String key = entry.getKey();
            List<Element> group = entry.getValue();
            createOutputFile(key, grpHdr, group);
        }
    }

    public static Map<String, List<Element>> groupByFallback(Document doc, NodeList pmtInfList, String[][] fallbackPaths) {
        Map<String, List<Element>> grouped = new HashMap<String, List<Element>>();
        for (int i = 0; i < pmtInfList.getLength(); i++) {
            Element pmtInf = (Element) pmtInfList.item(i);
            String key = resolveFirstNonEmptyTag(pmtInf, fallbackPaths);
            if (!grouped.containsKey(key)) {
                grouped.put(key, new ArrayList<Element>());
            }
            grouped.get(key).add(pmtInf);
        }
        return grouped;
    }

    public static String resolveFirstNonEmptyTag(Element base, String[][] paths) {
        for (int i = 0; i < paths.length; i++) {
            String value = getNestedTagValue(base, paths[i]);
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return "UNKNOWN";
    }

    private static String getNestedTagValue(Element element, String[] path) {
        Element current = element;
        for (int i = 0; i < path.length; i++) {
            NodeList list = current.getElementsByTagNameNS("*", path[i]);
            if (list.getLength() == 0) {
                return null;
            }
            current = (Element) list.item(0);
        }
        return current.getTextContent();
    }

    private static void createOutputFile(String key, Node grpHdr, List<Element> paymentInfos) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document outDoc = dBuilder.newDocument();

        Element envelope = outDoc.createElementNS("urn:swift;xsd:envelope", "env:Envelope");
        envelope.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        outDoc.appendChild(envelope);

        Element appHdr = outDoc.createElementNS("urn:iso:std:iso:20022:tech-xsd:head.001.001.02", "AppHdr");

        appHdr.appendChild(createBICNode(outDoc, "Fr", "SENDERBIC", key));
        String receiverBIC = getReceiverBIC(paymentInfos.get(0));
        appHdr.appendChild(createBICNode(outDoc, "To", "RECEIVERBIC", receiverBIC));

        appHdr.appendChild(createTextElement(outDoc, "BizMsgldr", "2502271510350696"));
        appHdr.appendChild(createTextElement(outDoc, "MsgDefldr", "pacs.001.001.03"));
        appHdr.appendChild(createTextElement(outDoc, "BizSvc", "swift.cbprplus.02"));
        appHdr.appendChild(createTextElement(outDoc, "CreDt", "2025-02-27T22:57:47+01:00"));

        envelope.appendChild(appHdr);

        Element document = outDoc.createElementNS("urn:iso:std:iso:20022:tech:xsd:pain.001.001.03", "Document");
        Element cstmrCdtTrfInitn = outDoc.createElement("CstmrCdtTrfInitn");

        Node copiedGrpHdr = outDoc.importNode(grpHdr, true);
        updateGrpHdr((Element) copiedGrpHdr, paymentInfos);
        cstmrCdtTrfInitn.appendChild(copiedGrpHdr);

        for (int i = 0; i < paymentInfos.size(); i++) {
            Element pmtInf = paymentInfos.get(i);
            Node imported = outDoc.importNode(pmtInf, true);
            cstmrCdtTrfInitn.appendChild(imported);
        }

        document.appendChild(cstmrCdtTrfInitn);
        envelope.appendChild(document);

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        StreamResult result = new StreamResult(new File(key + ".txt"));
        DOMSource source = new DOMSource(outDoc);
        transformer.transform(source, result);
    }

    private static Element createBICNode(Document doc, String tagName, String placeholder, String bicValue) {
        Element tag = doc.createElement(tagName);
        Element fld = doc.createElement("FIld");
        Element fin = doc.createElement("Finlnstnld");
        Element bic = doc.createElement("BICFI");
        bic.setTextContent(bicValue != null ? bicValue : placeholder);
        fin.appendChild(bic);
        fld.appendChild(fin);
        tag.appendChild(fld);
        return tag;
    }

    private static Element createTextElement(Document doc, String tag, String value) {
        Element element = doc.createElement(tag);
        element.setTextContent(value);
        return element;
    }

    private static String getReceiverBIC(Element pmtInf) {
        NodeList txInfos = pmtInf.getElementsByTagNameNS("*", "CdtTrfTxInf");
        if (txInfos.getLength() == 0) return "RECEIVERBIC";
        Element tx = (Element) txInfos.item(0);
        return getNestedTagValue(tx, new String[]{"CdtrAgt", "FinInstnId", "BIC"});
    }

    private static void updateGrpHdr(Element grpHdr, List<Element> pmtInfos) {
        int totalTx = 0;
        double sum = 0.0;

        for (int i = 0; i < pmtInfos.size(); i++) {
            Element pmtInf = pmtInfos.get(i);
            NodeList txList = pmtInf.getElementsByTagNameNS("*", "CdtTrfTxInf");
            totalTx += txList.getLength();

            for (int j = 0; j < txList.getLength(); j++) {
                Element tx = (Element) txList.item(j);
                NodeList amts = tx.getElementsByTagNameNS("*", "InstdAmt");
                if (amts.getLength() > 0) {
                    sum += Double.parseDouble(amts.item(0).getTextContent());
                }
            }
        }

        NodeList nbTxsList = grpHdr.getElementsByTagNameNS("*", "NbOfTxs");
        if (nbTxsList.getLength() > 0) {
            nbTxsList.item(0).setTextContent(String.valueOf(totalTx));
        }

        NodeList ctrlSumList = grpHdr.getElementsByTagNameNS("*", "CtrlSum");
        if (ctrlSumList.getLength() > 0) {
            ctrlSumList.item(0).setTextContent(String.format("%.2f", sum));
        }
    }
}
