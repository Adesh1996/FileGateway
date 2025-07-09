import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.*;




/**
 * This is the code which does not gives extra line after each tag
 * @author ADMIN
 *
 */
public class PainFileBatchSplitter {
    public static void main(String[] args) throws Exception {
    	File inputFile = new File("C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\SMSRFT_PAIN1V3.xml");
       // File inputFile = new File("SMSRFT_PAIN1V3.xml");

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(inputFile);
        doc.getDocumentElement().normalize();

        Node grpHdr = doc.getElementsByTagNameNS("*", "GrpHdr").item(0);
        NodeList pmtInfList = doc.getElementsByTagNameNS("*", "PmtInf");

        Map<String, List<Element>> bicMap = new HashMap<String, List<Element>>();

        for (int i = 0; i < pmtInfList.getLength(); i++) {
            Element pmtInf = (Element) pmtInfList.item(i);
            Node dbtrAgt = pmtInf.getElementsByTagNameNS("*", "DbtrAgt").item(0);
            String bic = getBIC(dbtrAgt);

            if (!bicMap.containsKey(bic)) {
                bicMap.put(bic, new ArrayList<Element>());
            }
            bicMap.get(bic).add(pmtInf);
        }

        Iterator<Map.Entry<String, List<Element>>> iterator = bicMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, List<Element>> entry = iterator.next();
            String bic = entry.getKey();
            List<Element> pmtList = entry.getValue();

            Document newDoc = dBuilder.newDocument();

            Element newDocument = newDoc.createElementNS("urn:iso:std:iso:20022:tech:xsd:pain.001.001.03", "Document");
            newDoc.appendChild(newDocument);

            Element newRoot = newDoc.createElement("CstmrCdtTrfInitn");
            newDocument.appendChild(newRoot);

            Node newGrpHdr = newDoc.importNode(grpHdr, true);
            Element newGrpHdrElem = (Element) newGrpHdr;

            int totalTxs = 0;
            double totalSum = 0.0;

            for (int i = 0; i < pmtList.size(); i++) {
                Element pmt = pmtList.get(i);
                NodeList txList = pmt.getElementsByTagNameNS("*", "CdtTrfTxInf");
                totalTxs += txList.getLength();

                for (int j = 0; j < txList.getLength(); j++) {
                    Element tx = (Element) txList.item(j);
                    Node amtNode = tx.getElementsByTagNameNS("*", "InstdAmt").item(0);
                    double value = Double.parseDouble(amtNode.getTextContent());
                    totalSum += value;
                }
            }

            newGrpHdrElem.getElementsByTagNameNS("*", "NbOfTxs").item(0).setTextContent(String.valueOf(totalTxs));
            newGrpHdrElem.getElementsByTagNameNS("*", "CtrlSum").item(0).setTextContent(String.format("%.2f", totalSum));
            newRoot.appendChild(newGrpHdrElem);

            for (int i = 0; i < pmtList.size(); i++) {
                Node importedPmt = newDoc.importNode(pmtList.get(i), true);
                newRoot.appendChild(importedPmt);
            }

            // Write output without extra newlines
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();

            // This prevents indentation that causes extra blank lines
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
           // transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            DOMSource domSource = new DOMSource(newDoc);
            StreamResult result = new StreamResult(new File("Batch_" + bic + ".txt"));
            transformer.transform(domSource, result);

            System.out.println("Created: Batch_" + bic + ".txt");
        }
    }

    private static String getBIC(Node dbtrAgt) {
        Element dbtrAgtElement = (Element) dbtrAgt;
        NodeList bicNodes = dbtrAgtElement.getElementsByTagNameNS("*", "BIC");
        if (bicNodes.getLength() > 0) {
            return bicNodes.item(0).getTextContent().trim();
        }
        return "UNKNOWN";
    }
}
