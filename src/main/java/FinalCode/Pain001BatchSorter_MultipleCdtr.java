package FinalCode;




/**
 * This is the final code which will seprate batch according to dbtr agt bic and cdtr agt bic 
 * @author ADMIN
 *
 */



import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.*;

public class Pain001BatchSorter_MultipleCdtr {

    private static final String NS = "urn:iso:std:iso:20022:tech:xsd:pain.001.001.03";

    public static void main(String[] args) throws Exception {
        processFile("C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\SMSRFT_PAIN1V3.xml", "cdtragtbic");
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
        Map<String, List<Element>> bicGroups = new HashMap<String, List<Element>>();

        for (int i = 0; i < pmtInfs.getLength(); i++) {
            Element originalPmtInf = (Element) pmtInfs.item(i);
            NodeList cdtTrfTxInfList = originalPmtInf.getElementsByTagNameNS(NS, "CdtTrfTxInf");

            Element dbtrAgt = (Element) originalPmtInf.getElementsByTagNameNS(NS, "DbtrAgt").item(0);
            Element pmtInfId = (Element) originalPmtInf.getElementsByTagNameNS(NS, "PmtInfId").item(0);

            for (int j = 0; j < cdtTrfTxInfList.getLength(); j++) {
                Element tx = (Element) cdtTrfTxInfList.item(j);
                String cdtrAgtBic = getCdtrAgtBic(tx);

                Document singleTxDoc = dBuilder.newDocument();
                Element pmtInf = singleTxDoc.createElementNS(NS, "PmtInf");
                singleTxDoc.appendChild(pmtInf);

                // Clone basic elements
                pmtInf.appendChild(singleTxDoc.importNode(pmtInfId, true));
                pmtInf.appendChild(singleTxDoc.importNode(dbtrAgt, true));
                pmtInf.appendChild(singleTxDoc.importNode(tx, true));

                if (!bicGroups.containsKey(cdtrAgtBic)) {
                    bicGroups.put(cdtrAgtBic, new ArrayList<Element>());
                }
                bicGroups.get(cdtrAgtBic).add(pmtInf);
            }
        }

        // Output files
        Iterator<Map.Entry<String, List<Element>>> it = bicGroups.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, List<Element>> entry = it.next();
            String cdtrBic = entry.getKey();
            List<Element> groupedPmts = entry.getValue();

            Document outputDoc = dBuilder.newDocument();
            Element document = outputDoc.createElementNS(NS, "Document");
            outputDoc.appendChild(document);
            
            
            Element appHdr = createAppHdr(outputDoc, getDbtrBic(groupedPmts.get(0)), getReceiverBic(groupedPmts), creationDate, msgId);
            document.appendChild(appHdr);
            
            
            Element cstmrCdtTrfInitn = outputDoc.createElementNS(NS, "CstmrCdtTrfInitn");
            document.appendChild(cstmrCdtTrfInitn);

            Node copiedGrpHdr = outputDoc.importNode(grpHdr, true);
            updateGrpHdrTotals(outputDoc, copiedGrpHdr, groupedPmts);
            cstmrCdtTrfInitn.appendChild(copiedGrpHdr);

            for (int i = 0; i < groupedPmts.size(); i++) {
                cstmrCdtTrfInitn.appendChild(outputDoc.importNode(groupedPmts.get(i), true));
            }

            String fileName = "output_cdtragtbic_" + cdtrBic + ".xml";
            removeWhitespaceNodes(document.getOwnerDocument());
            writeXmlToFile(outputDoc, fileName);
            System.out.println("Written: " + fileName);
        }
    }

    private static String getCdtrAgtBic(Element tx) {
        return tx.getElementsByTagNameNS(NS, "BIC").item(0).getTextContent();
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
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
       //   transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "1");
        
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(filename));
        transformer.transform(source, result);
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
       // String hdrNS = "urn:iso:std:iso:20022:tech:xsd:head.001.001.02";
       // appendTextElementNS(doc, appHdr, hdrNS, "BizMsgIdr", msgId);
       // appendTextElementNS(doc, appHdr, hdrNS, "MsgDefIdr", "pain.001.001.03");
       // appendTextElementNS(doc, appHdr, hdrNS, "BizSvc", "swift.cbprplus.02");
       // appendTextElementNS(doc, appHdr, hdrNS, "CreDt", creationDate);

        return appHdr;
    }
    
    private static void appendTextElement(Document doc, Element parent, String name, String value) {
        Element el = doc.createElement(name);
        el.setTextContent(value);
        parent.appendChild(el);
    }
    
    private static void appendTextElementNS(Document doc, Element parent, String ns, String name, String value) {
        Element el = doc.createElementNS(ns, name);
        el.setTextContent(value);
        parent.appendChild(el);
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
