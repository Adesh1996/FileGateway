package PainFileGenerator;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class PainFileManipulator {

    public static void process(File inputFile, File outputFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document doc = factory.newDocumentBuilder().parse(inputFile);
        doc.getDocumentElement().normalize();

        String ns = doc.getDocumentElement().getNamespaceURI();
        Element root = doc.getDocumentElement();
        Element core = getFirstElementByDepth(root);

        if (core == null) throw new IllegalArgumentException("Missing core element under <Document>");

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String msgId = "MSG-" + timestamp;

        // Update GrpHdr
        Element grpHdr = (Element) getFirstElementByTag(core, ns, "GrpHdr");
        if (grpHdr != null) {
            setText(grpHdr, ns, "MsgId", msgId);
            setText(grpHdr, ns, "CreDtTm", OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
        }

        int totalTxns = 0;
        double totalSum = 0.0;

        NodeList pmtList = core.getElementsByTagNameNS(ns, "PmtInf");
        for (int i = 0; i < pmtList.getLength(); i++) {
            Element pmt = (Element) pmtList.item(i);
            String batchId = msgId + "-B" + (i + 1);
            setText(pmt, ns, "PmtInfId", batchId);
            setText(pmt, ns, "ReqdExctnDt", LocalDate.now().toString());

            NodeList txns = pmt.getElementsByTagNameNS(ns, "CdtTrfTxInf");
            int count = txns.getLength();
            double sum = 0.0;

            for (int j = 0; j < count; j++) {
                Element txn = (Element) txns.item(j);
                String endToEndId = batchId + "-T" + (j + 1);
                Element pmtId = (Element) txn.getElementsByTagNameNS(ns, "PmtId").item(0);
                setText(pmtId, ns, "EndToEndId", endToEndId);

                Element amt = (Element) txn.getElementsByTagNameNS(ns, "Amt").item(0);
                Element instdAmt = (Element) amt.getElementsByTagNameNS(ns, "InstdAmt").item(0);
                if (instdAmt != null) {
                    sum += Double.parseDouble(instdAmt.getTextContent());
                }
            }

            setText(pmt, ns, "NbOfTxs", String.valueOf(count));
            setText(pmt, ns, "CtrlSum", formatAmount(sum));

            totalTxns += count;
            totalSum += sum;
        }

        // Update totals in GrpHdr
        if (grpHdr != null) {
            setText(grpHdr, ns, "NbOfTxs", String.valueOf(totalTxns));
            setText(grpHdr, ns, "CtrlSum", formatAmount(totalSum));
        }

        save(doc, outputFile);
        System.out.println("✓ Updated file written to: " + outputFile.getAbsolutePath());
    }

    private static void setText(Element parent, String ns, String tag, String value) {
        NodeList nodes = parent.getElementsByTagNameNS(ns, tag);
        if (nodes.getLength() > 0) {
            nodes.item(0).setTextContent(value);
        }
    }

    private static String formatAmount(double amt) {
        DecimalFormat df = new DecimalFormat("0.00");
        return df.format(amt);
    }

    private static Element getFirstElementByDepth(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                return (Element) children.item(i);
            }
        }
        return null;
    }

    private static Element getFirstElementByTag(Element parent, String ns, String tag) {
        NodeList list = parent.getElementsByTagNameNS(ns, tag);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private static void save(Document doc, File file) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer tr = tf.newTransformer();
        tr.setOutputProperty(OutputKeys.INDENT, "yes");
        tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        tr.transform(new DOMSource(doc), new StreamResult(file));
    }
} 
