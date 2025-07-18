package NFT;

import javax.xml.stream.*;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class PainFileStreamingGenerator {

    public static void main(String[] args) throws Exception {
        String inputFile = "C:\\Users\\ADMIN\\OneDrive\\Desktop\\RFT\\SMSRFT_PAIN1V3.xml";
        int totalTxns = 50000;
        int totalBatches = 100;
        int outputFiles = 5;

        int txnsPerFile = totalTxns / outputFiles;
        int batchesPerFile = totalBatches / outputFiles;

        for (int fileIndex = 1; fileIndex <= outputFiles; fileIndex++) {
            String outputFile = "output_streamed_part" + fileIndex + ".xml";
            generateStreamingXML(inputFile, outputFile, txnsPerFile, batchesPerFile, fileIndex);
        }
    }

    public static void generateStreamingXML(String inputTemplatePath, String outputPath,
                                            int totalTxns, int batchCount, int filePart) throws Exception {

        InputStream in = new FileInputStream(inputTemplatePath);
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        XMLStreamReader reader = inputFactory.createXMLStreamReader(in);

        // Extract <PmtInf> and <CdtTrfTxInf> as templates
        StringWriter pmtInfWriter = new StringWriter();
        StringWriter txnWriter = new StringWriter();
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        XMLStreamWriter pmtWriter = factory.createXMLStreamWriter(pmtInfWriter);
        XMLStreamWriter txWriter = factory.createXMLStreamWriter(txnWriter);

        boolean copy = false;
        int depth = 0;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT && reader.getLocalName().equals("PmtInf")) {
                copy = true;
                depth = 1;
                pmtWriter.writeStartElement(reader.getLocalName());
            } else if (copy && event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                pmtWriter.writeStartElement(reader.getLocalName());
            } else if (copy && event == XMLStreamConstants.END_ELEMENT) {
                pmtWriter.writeEndElement();
                depth--;
                if (depth == 0) break;
            }
        }

        reader.close();
        in.close();
        pmtWriter.close();

        // Reset for txn
        in = new FileInputStream(inputTemplatePath);
        reader = inputFactory.createXMLStreamReader(in);

        copy = false;
        depth = 0;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT && reader.getLocalName().equals("CdtTrfTxInf")) {
                copy = true;
                depth = 1;
                txWriter.writeStartElement(reader.getLocalName());
            } else if (copy && event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                txWriter.writeStartElement(reader.getLocalName());
            } else if (copy && event == XMLStreamConstants.END_ELEMENT) {
                txWriter.writeEndElement();
                depth--;
                if (depth == 0) break;
            }
        }

        txWriter.close();
        reader.close();
        in.close();

        // Start writing full output XML
        OutputStream out = new FileOutputStream(outputPath);
        XMLStreamWriter writer = factory.createXMLStreamWriter(out, "UTF-8");

        writer.writeStartDocument("UTF-8", "1.0");
        writer.writeStartElement("Document");
        writer.writeDefaultNamespace("urn:iso:std:iso:20022:tech:xsd:pain.001.001.03");
        writer.writeStartElement("CstmrCdtTrfInitn");

        String msgId = "MSG-F" + filePart + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        writer.writeStartElement("GrpHdr");
        writer.writeStartElement("MsgId");
        writer.writeCharacters(msgId);
        writer.writeEndElement();
        writer.writeStartElement("CreDtTm");
        writer.writeCharacters(OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
        writer.writeEndElement();
        writer.writeStartElement("NbOfTxs");
        writer.writeCharacters(String.valueOf(totalTxns));
        writer.writeEndElement();
        writer.writeStartElement("CtrlSum");
        writer.writeCharacters(String.format("%.2f", totalTxns * 100.00));
        writer.writeEndElement();
        writer.writeStartElement("InitgPty");
        writer.writeStartElement("Nm");
        writer.writeCharacters("AutoGen");
        writer.writeEndElement();
        writer.writeEndElement();
        writer.writeEndElement(); // GrpHdr

        int txnPerBatch = totalTxns / batchCount;
        int extra = totalTxns % batchCount;
        int txnId = 1;

        for (int i = 1; i <= batchCount; i++) {
            int txnsThisBatch = txnPerBatch + (i <= extra ? 1 : 0);
            writer.writeStartElement("PmtInf");
            writer.writeStartElement("PmtInfId");
            writer.writeCharacters(msgId + "-B" + i);
            writer.writeEndElement();
            writer.writeStartElement("ReqdExctnDt");
            writer.writeCharacters(LocalDate.now().toString());
            writer.writeEndElement();
            writer.writeStartElement("NbOfTxs");
            writer.writeCharacters(String.valueOf(txnsThisBatch));
            writer.writeEndElement();
            writer.writeStartElement("CtrlSum");
            writer.writeCharacters(String.format("%.2f", txnsThisBatch * 100.00));
            writer.writeEndElement();

            for (int j = 1; j <= txnsThisBatch; j++) {
                writer.writeStartElement("CdtTrfTxInf");
                writer.writeStartElement("PmtId");
                writer.writeStartElement("EndToEndId");
                writer.writeCharacters(msgId + "-B" + i + "-T" + j);
                writer.writeEndElement();
                writer.writeEndElement();
                writer.writeStartElement("Amt");
                writer.writeStartElement("InstdAmt");
                writer.writeAttribute("Ccy", "EUR");
                writer.writeCharacters("100.00");
                writer.writeEndElement();
                writer.writeEndElement();
                writer.writeEndElement(); // CdtTrfTxInf
                txnId++;
            }

            writer.writeEndElement(); // PmtInf
        }

        writer.writeEndElement(); // CstmrCdtTrfInitn
        writer.writeEndElement(); // Document
        writer.writeEndDocument();
        writer.flush();
        writer.close();

        System.out.println("✅ File generated: " + outputPath);
    }
}
