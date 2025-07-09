
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Pain001BatchSeparatorOutputFolder {

    public static void main(String[] args) {
        try {
            // Input file
            File inputFile = new File("C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\SMSRFT_PAIN1V3.xml");

            // Create output directory if not exists
            File outputDir = new File("output");
            if (!outputDir.exists()) {
                outputDir.mkdir();
                System.out.println("Created output folder at: " + outputDir.getAbsolutePath());
            }

            // Parse input XML
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(true);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputFile);
            doc.getDocumentElement().normalize();

            NodeList pmtInfList = doc.getElementsByTagNameNS("*", "PmtInf");

            // Maps
            Map<String, Document> bicDocumentMap = new HashMap<String, Document>();
            Map<String, Element> bicGrpHdrMap = new HashMap<String, Element>();
            Map<String, Integer> bicTxCount = new HashMap<String, Integer> ();
            Map<String, Double> bicCtrlSum = new HashMap<String, Double>();

            for (int i = 0; i < pmtInfList.getLength(); i++) {
                Element pmtInf = (Element) pmtInfList.item(i);

                NodeList bicList = pmtInf.getElementsByTagNameNS("*", "BIC");
                if (bicList.getLength() > 0) {
                    String bic = bicList.item(0).getTextContent();

                    Document bicDoc = bicDocumentMap.get(bic);
                    if (bicDoc == null) {
                        bicDoc = dBuilder.newDocument();
                        Element documentElement = bicDoc.createElementNS("urn:iso:std:iso:20022:tech:xsd:pain.001.001.03", "Document");
                        Element cstmrCdtTrfInitn = bicDoc.createElement("CstmrCdtTrfInitn");
                        documentElement.appendChild(cstmrCdtTrfInitn);
                        bicDoc.appendChild(documentElement);

                        Element grpHdr = bicDoc.createElement("GrpHdr");
                        Element msgId = bicDoc.createElement("MsgId");
                        msgId.setTextContent("MSG-" + bic);
                        Element creDtTm = bicDoc.createElement("CreDtTm");
                        creDtTm.setTextContent(java.time.LocalDateTime.now().toString());
                        Element nbOfTxs = bicDoc.createElement("NbOfTxs");
                        nbOfTxs.setTextContent("0");
                        Element ctrlSum = bicDoc.createElement("CtrlSum");
                        ctrlSum.setTextContent("0.00");
                        Element initgPty = bicDoc.createElement("InitgPty");
                        Element nm = bicDoc.createElement("Nm");
                        nm.setTextContent("Sample Company Ltd");
                        initgPty.appendChild(nm);

                        grpHdr.appendChild(msgId);
                        grpHdr.appendChild(creDtTm);
                        grpHdr.appendChild(nbOfTxs);
                        grpHdr.appendChild(ctrlSum);
                        grpHdr.appendChild(initgPty);

                        cstmrCdtTrfInitn.appendChild(grpHdr);

                        bicDocumentMap.put(bic, bicDoc);
                        bicGrpHdrMap.put(bic, grpHdr);
                        bicTxCount.put(bic, 0);
                        bicCtrlSum.put(bic, 0.0);
                    }

                    Document currentDoc = bicDoc;
                    Element cstmrCdtTrfInitn = (Element) currentDoc.getDocumentElement().getFirstChild();
                    Node importedPmtInf = currentDoc.importNode(pmtInf, true);
                    cstmrCdtTrfInitn.appendChild(importedPmtInf);

                    int txCountInBatch = Integer.parseInt(pmtInf.getElementsByTagNameNS("*", "NbOfTxs").item(0).getTextContent());
                    bicTxCount.put(bic, bicTxCount.get(bic) + txCountInBatch);

                    double batchSum = Double.parseDouble(pmtInf.getElementsByTagNameNS("*", "CtrlSum").item(0).getTextContent());
                    bicCtrlSum.put(bic, bicCtrlSum.get(bic) + batchSum);
                }
            }

            // Update GrpHdr and save files
            for (String bic : bicDocumentMap.keySet()) {
                Document batchDoc = bicDocumentMap.get(bic);
                Element grpHdr = bicGrpHdrMap.get(bic);

                grpHdr.getElementsByTagName("NbOfTxs").item(0).setTextContent(String.valueOf(bicTxCount.get(bic)));
                grpHdr.getElementsByTagName("CtrlSum").item(0).setTextContent(String.format("%.2f", bicCtrlSum.get(bic)));

                // Prepare output file path
                String outputFilePath = "output/Batch_" + bic + ".xml";

                // Write to file
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
                transformer.setOutputProperty(OutputKeys.METHOD, "xml");

                DOMSource source = new DOMSource(batchDoc);
                StreamResult result = new StreamResult(new File(outputFilePath));
                transformer.transform(source, result);

                System.out.println("Created XML file for BIC: " + bic + " at " + outputFilePath);
            }

            System.out.println("Batch separation and folder organization complete!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
