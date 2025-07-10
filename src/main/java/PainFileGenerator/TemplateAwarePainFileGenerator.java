package PainFileGenerator;


import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class TemplateAwarePainFileGenerator {
	
	public static void main(String[] args) throws Exception {
		TemplateAwarePainFileGenerator.run(new File("F:\\Barclays BFG Project\\FileGateway\\input\\ImportantTestFile.xml"),1500,20,new File("output.xml"));
	}

    public static void run(File inputTemplate, int totalTransactions, int batchCount, File outputFile) throws Exception {
        Map<Integer, Integer> batchMap = distributeTransactions(totalTransactions, batchCount);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document template = dbf.newDocumentBuilder().parse(inputTemplate);
        template.getDocumentElement().normalize();

        Element documentRoot = template.getDocumentElement();
        String rootNs = documentRoot.getNamespaceURI();

        Node appHdr = getNode(template, "AppHdr");
        Element coreBlock = getFirstElementByDepth(documentRoot);
        if (coreBlock == null) {
            throw new RuntimeException("Unable to detect core block (e.g., CstmrCdtTrfInitn) from template.");
        }
        String coreBlockName = coreBlock.getLocalName();

        Element grpHdr = (Element) getNode(template, "GrpHdr");
        Element pmtInfTemplate = (Element) getNode(template, "PmtInf");
        Element txnTemplate = (Element) getNode(template, detectTxnTag(coreBlockName));

        XMLOutputFactory xof = XMLOutputFactory.newInstance();
        XMLStreamWriter writer = xof.createXMLStreamWriter(new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8));

        writer.writeStartDocument("UTF-8", "1.0");
        writer.writeStartElement("Document");
        writer.writeDefaultNamespace(rootNs);

        writer.writeStartElement(coreBlockName);

        if (appHdr != null) {
            copyNode(appHdr, writer);
        }

        String msgId = "MSG-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        writer.writeStartElement("GrpHdr");
        writeTag(writer, "MsgId", msgId);
        writeTag(writer, "CreDtTm", OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
        writeTag(writer, "NbOfTxs", String.valueOf(totalTransactions));
        writeTag(writer, "CtrlSum", String.format("%.2f", totalTransactions * 100.0));
        writer.writeEndElement(); // GrpHdr

        for (int b = 1; b <= batchCount; b++) {
            int txnCount = batchMap.get(b);
            double ctrlSum = txnCount * 100.0;

            writer.writeStartElement("PmtInf");
            writeTag(writer, "PmtInfId", msgId + "-B" + b);
            writeTag(writer, "ReqdExctnDt", LocalDate.now().toString());
            writeTag(writer, "NbOfTxs", String.valueOf(txnCount));
            writeTag(writer, "CtrlSum", String.format("%.2f", ctrlSum));

            for (int t = 1; t <= txnCount; t++) {
                writer.writeStartElement("CdtTrfTxInf");
                writer.writeStartElement("PmtId");
                writeTag(writer, "EndToEndId", msgId + "-B" + b + "-T" + t);
                writer.writeEndElement(); // PmtId

                writer.writeStartElement("Amt");
                writer.writeStartElement("InstdAmt");
                writer.writeAttribute("Ccy", "EUR");
                writer.writeCharacters("100.00");
                writer.writeEndElement();
                writer.writeEndElement(); // Amt

                writer.writeEndElement(); // CdtTrfTxInf
            }

            writer.writeEndElement(); // PmtInf
        }

        writer.writeEndElement(); // core block
        writer.writeEndElement(); // Document
        writer.writeEndDocument();

        writer.flush();
        writer.close();

        System.out.println("[✓] PAIN file generated at: " + outputFile.getAbsolutePath());
    }

    private static Map<Integer, Integer> distributeTransactions(int total, int batches) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        int base = total / batches, rem = total % batches;
        for (int i = 1; i <= batches; i++) {
            map.put(i, base + (i <= rem ? 1 : 0));
        }
        return map;
    }

    private static void writeTag(XMLStreamWriter writer, String tag, String value) throws Exception {
        writer.writeStartElement(tag);
        writer.writeCharacters(value);
        writer.writeEndElement();
    }

    private static Node getNode(Document doc, String tag) {
        NodeList list = doc.getElementsByTagNameNS("*", tag);
        return list.getLength() > 0 ? list.item(0) : null;
    }

    private static Element getFirstElementByDepth(Node parent) {
        NodeList list = parent.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) node;
            }
        }
        return null;
    }

    private static String detectTxnTag(String coreBlockName) {
        if (coreBlockName == null) {
            throw new IllegalArgumentException("Core block name is null. Check the structure of the template file.");
        }
        switch (coreBlockName) {
            case "CstmrCdtTrfInitn": return "CdtTrfTxInf";
            case "CstmrDrctDbtInitn": return "DrctDbtTxInf";
            case "CstmrPmtRvsl": return "TxInf";
            default: return "CdtTrfTxInf";
        }
    }

    private static void copyNode(Node node, XMLStreamWriter writer) throws Exception {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            writer.writeStartElement(node.getLocalName());
            NamedNodeMap attrs = node.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                Node attr = attrs.item(i);
                writer.writeAttribute(attr.getNodeName(), attr.getNodeValue());
            }
            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                copyNode(children.item(i), writer);
            }
            writer.writeEndElement();
        } else if (node.getNodeType() == Node.TEXT_NODE) {
            writer.writeCharacters(node.getTextContent());
        }
    }
}
