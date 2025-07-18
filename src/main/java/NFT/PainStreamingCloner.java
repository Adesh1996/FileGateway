package NFT;

import javax.xml.stream.*;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class PainStreamingCloner {

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Enter input XML template path:");
        String inputPath = scanner.nextLine();

        System.out.println("Enter total number of transactions:");
        int totalTxns = scanner.nextInt();

        System.out.println("Enter number of batches:");
        int batches = scanner.nextInt();

        System.out.println("Enter number of output files:");
        int files = scanner.nextInt();

        int batchPerFile = batches / files;
        int extra = batches % files;

        int startBatch = 1;
        for (int f = 1; f <= files; f++) {
            int endBatch = startBatch + batchPerFile - 1;
            if (f <= extra) endBatch++;

            String output = "pain_output_F" + f + ".xml";
            generateFile(inputPath, output, totalTxns, batches, startBatch, endBatch);

            startBatch = endBatch + 1;
        }

        System.out.println("\n✅ All files generated successfully.");
    }

    public static void generateFile(String inputPath, String outputPath, int totalTxns, int totalBatches,
                                    int batchStart, int batchEnd) throws Exception {
        FileInputStream in = new FileInputStream(inputPath);
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        XMLStreamReader reader = inputFactory.createXMLStreamReader(in);

        XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
        FileOutputStream out = new FileOutputStream(outputPath);
        XMLStreamWriter writer = outputFactory.createXMLStreamWriter(out, "UTF-8");

        DateTimeFormatter msgIdFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
        String msgId = "MSG-F" + batchStart + "-" + LocalDateTime.now().format(msgIdFormatter);
        String creDtTm = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        String execDate = LocalDate.now().toString();

        reader.nextTag(); // Envelope
        reader.nextTag(); // Document
        String documentName = reader.getLocalName();
        String namespace = reader.getNamespaceURI();
        reader.nextTag();
        String coreType = reader.getLocalName();

        String fileFormat = namespace.substring(namespace.lastIndexOf("/") + 1);
        System.out.println("Detected file format: " + fileFormat);

        // Capture GrpHdr, PmtInf, and CdtTrfTxInf templates
        StringWriter grpHdrWriter = new StringWriter();
        StringWriter pmtInfWriter = new StringWriter();
        StringWriter txnTemplateWriter = new StringWriter();
        XMLStreamWriter grpWriter = outputFactory.createXMLStreamWriter(grpHdrWriter);
        XMLStreamWriter pmtWriter = outputFactory.createXMLStreamWriter(pmtInfWriter);
        XMLStreamWriter txnWriter = outputFactory.createXMLStreamWriter(txnTemplateWriter);

        boolean inGrpHdr = false, inPmtInf = false, inTxn = false;
        int depth = 0;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = reader.getLocalName();
                if (name.equals("GrpHdr")) {
                    inGrpHdr = true; depth = 1; grpWriter.writeStartElement(name);
                } else if (inGrpHdr && depth > 0) {
                    grpWriter.writeStartElement(name); depth++;
                } else if (name.equals("PmtInf")) {
                    inPmtInf = true; depth = 1; pmtWriter.writeStartElement(name);
                } else if (inPmtInf && name.equals("CdtTrfTxInf")) {
                    inTxn = true; depth = 1; txnWriter.writeStartElement(name);
                } else if (inPmtInf && inTxn && depth > 0) {
                    txnWriter.writeStartElement(name); depth++;
                } else if (inPmtInf && depth > 0) {
                    pmtWriter.writeStartElement(name); depth++;
                }
            } else if (event == XMLStreamConstants.CHARACTERS && !reader.isWhiteSpace()) {
                if (inGrpHdr) grpWriter.writeCharacters(reader.getText());
                else if (inPmtInf && inTxn) txnWriter.writeCharacters(reader.getText());
                else if (inPmtInf) pmtWriter.writeCharacters(reader.getText());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                String name = reader.getLocalName();
                if (inTxn) {
                    txnWriter.writeEndElement(); depth--;
                    if (depth == 0) { inTxn = false; }
                } else if (inPmtInf) {
                    pmtWriter.writeEndElement(); depth--;
                    if (depth == 0) { inPmtInf = false; break; }
                } else if (inGrpHdr) {
                    grpWriter.writeEndElement(); depth--;
                    if (depth == 0) inGrpHdr = false;
                }
            }
        }
        grpWriter.close();
        pmtWriter.close();
        txnWriter.close();

        String grpHdrXML = grpHdrWriter.toString();
        String pmtInfXML = pmtInfWriter.toString();
        String txnTemplateXML = txnTemplateWriter.toString();

        // Start writing output document
        writer.writeStartDocument("UTF-8", "1.0");
        writer.writeStartElement(documentName);
        writer.writeDefaultNamespace(namespace);
        writer.writeStartElement(coreType);

        writer.writeCharacters("\n");
        writer.flush();
        out.write(grpHdrXML.replaceAll("<MsgId>.*?</MsgId>", "<MsgId>" + msgId + "</MsgId>")
                            .replaceAll("<CreDtTm>.*?</CreDtTm>", "<CreDtTm>" + creDtTm + "</CreDtTm>")
                            .replaceAll("<NbOfTxs>.*?</NbOfTxs>", "<NbOfTxs>" + calculateTxns(batchStart, batchEnd, totalTxns, totalBatches) + "</NbOfTxs>")
                            .replaceAll("<CtrlSum>.*?</CtrlSum>", String.format("<CtrlSum>%.2f</CtrlSum>", 100.0 * calculateTxns(batchStart, batchEnd, totalTxns, totalBatches)))
                            .getBytes("UTF-8"));

        int txnId = 1;
        for (int b = batchStart; b <= batchEnd; b++) {
            int txnCount = calculateTxnsForBatch(b, totalTxns, totalBatches);
            String pmtBlock = pmtInfXML.replaceAll("<PmtInfId>.*?</PmtInfId>", "<PmtInfId>" + msgId + "-B" + b + "</PmtInfId>")
                                       .replaceAll("<ReqdExctnDt>.*?</ReqdExctnDt>", "<ReqdExctnDt>" + execDate + "</ReqdExctnDt>")
                                       .replaceAll("<NbOfTxs>.*?</NbOfTxs>", "<NbOfTxs>" + txnCount + "</NbOfTxs>")
                                       .replaceAll("<CtrlSum>.*?</CtrlSum>", String.format("<CtrlSum>%.2f</CtrlSum>", 100.0 * txnCount));
            writer.writeCharacters("\n");
            writer.flush();
            out.write(pmtBlock.getBytes("UTF-8"));

            for (int t = 1; t <= txnCount; t++) {
                String txnBlock = txnTemplateXML.replaceAll("<EndToEndId>.*?</EndToEndId>",
                        "<EndToEndId>" + msgId + "-B" + b + "-T" + txnId++ + "</EndToEndId>");
                writer.writeCharacters("\n");
                writer.flush();
                out.write(txnBlock.getBytes("UTF-8"));
            }
            writer.writeCharacters("\n</PmtInf>");
        }

        writer.writeEndElement(); // coreType
        writer.writeEndElement(); // Document
        writer.writeEndDocument();
        writer.flush();
        writer.close();
        reader.close();

        System.out.println("✅ File generated: " + outputPath);
    }

    private static int calculateTxns(int batchStart, int batchEnd, int totalTxns, int totalBatches) {
        int perBatch = totalTxns / totalBatches;
        int extra = totalTxns % totalBatches;
        int count = 0;
        for (int b = batchStart; b <= batchEnd; b++) {
            count += perBatch + (b <= extra ? 1 : 0);
        }
        return count;
    }

    private static int calculateTxnsForBatch(int b, int totalTxns, int totalBatches) {
        int base = totalTxns / totalBatches;
        int rem = totalTxns % totalBatches;
        return base + (b <= rem ? 1 : 0);
    }
}
