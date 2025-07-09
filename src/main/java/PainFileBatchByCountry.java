import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.*;

public class PainFileBatchByCountry {

    public static void main(String[] args) throws Exception {
    	File inputFile = new File("C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\SMSRFT_PAIN1V3.xml");
      
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(inputFile);
        doc.getDocumentElement().normalize();

        Node grpHdr = doc.getElementsByTagNameNS("*", "GrpHdr").item(0);
        NodeList pmtInfList = doc.getElementsByTagNameNS("*", "PmtInf");

        // Map grouped by country code (from BIC)
        Map<String, List<Element>> countryMap = new HashMap<String, List<Element>>();

        for (int i = 0; i < pmtInfList.getLength(); i++) {
            Element pmtInf = (Element) pmtInfList.item(i);
            Node dbtrAgt = pmtInf.getElementsByTagNameNS("*", "DbtrAgt").item(0);
            String bic = getBIC(dbtrAgt);

            String countryCode = "UNKNOWN";
            if (bic != null && bic.length() >= 6) {
                countryCode = bic.substring(4, 6); // chars 5 & 6 = country
            }

            if (!countryMap.containsKey(countryCode)) {
                countryMap.put(countryCode, new ArrayList<Element>());
            }
            countryMap.get(countryCode).add(pmtInf);
        }

        // Process each group
        Iterator<Map.Entry<String, List<Element>>> iterator = countryMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, List<Element>> entry = iterator.next();
            String countryCode = entry.getKey();
            List<Element> pmtList = entry.getValue();

            Document newDoc = dBuilder.newDocument();

            // Create <Document>
            Element newDocument = newDoc.createElementNS("urn:iso:std:iso:20022:tech:xsd:pain.001.001.03", "Document");
            newDoc.appendChild(newDocument);

            // Create <CstmrCdtTrfInitn>
            Element newRoot = newDoc.createElement("CstmrCdtTrfInitn");
            newDocument.appendChild(newRoot);

            // Clone <GrpHdr>
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

            // Update GrpHdr values
            newGrpHdrElem.getElementsByTagNameNS("*", "NbOfTxs").item(0).setTextContent(String.valueOf(totalTxs));
            newGrpHdrElem.getElementsByTagNameNS("*", "CtrlSum").item(0).setTextContent(String.format("%.2f", totalSum));
            newRoot.appendChild(newGrpHdrElem);

            // Append grouped <PmtInf>
            for (int i = 0; i < pmtList.size(); i++) {
                Node importedPmt = newDoc.importNode(pmtList.get(i), true);
                newRoot.appendChild(importedPmt);
            }

            // Write to output file
            String fileName = "Batch_" + countryCode + ".txt";
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();

            // Set formatting: no blank lines
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            DOMSource domSource = new DOMSource(newDoc);
            StreamResult sr = new StreamResult(new File(fileName));
            transformer.transform(domSource, sr);

            System.out.println("Created file: " + fileName);
        }
    }

    private static String getBIC(Node dbtrAgt) {
        if (dbtrAgt == null) return null;
        Element dbtrAgtElement = (Element) dbtrAgt;
        NodeList bicNodes = dbtrAgtElement.getElementsByTagNameNS("*", "BIC");
        if (bicNodes.getLength() > 0) {
            return bicNodes.item(0).getTextContent().trim();
        }
        return null;
    }
}
