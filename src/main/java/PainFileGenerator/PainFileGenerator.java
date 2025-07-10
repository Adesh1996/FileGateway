package PainFileGenerator;

import javax.xml.stream.*;
import javax.xml.stream.events.*; // Keep this import for StartElement, EndElement, etc.

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.math.BigDecimal; // For CtrlSum

public class PainFileGenerator {
	
	//TemplateAwarePainFileGenerator.run(new File("F:\\Barclays BFG Project\\FileGateway\\input\\ImportantTestFile.xml"),1500,20,new File("output.xml"));

    private static final String ORIGINAL_PAIN_FILE = "F:\\Barclays BFG Project\\FileGateway\\input\\ImportantTestFile.xml"; // Replace with your actual input file
    private static final String GENERATED_PAIN_FILE = "output_pain.xml"; // Output file

    public static void main(String[] args) {
		/*
		 * if (args.length < 2) { System.out.
		 * println("Usage: java PainFileGenerator <numBatches> <numTransactionsPerBatch>"
		 * ); return; }
		 */

    	 int numBatchesToGenerate = 50;
         int numTransactionsPerBatch = 2;
      //  int numBatchesToGenerate = Integer.parseInt(args[0]);
        //int numTransactionsPerBatch = Integer.parseInt(args[1]);

        try {
            generatePainFile(numBatchesToGenerate, numTransactionsPerBatch);
            System.out.println("PAIN file generated successfully: " + GENERATED_PAIN_FILE);
        } catch (XMLStreamException | IOException e) {
            System.err.println("Error generating PAIN file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void generatePainFile(int numBatches, int numTransactionsPerBatch)
            throws XMLStreamException, IOException {

        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();

        // Create a DTD-less parser for performance if DTD validation is not needed
        inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        XMLEventReader reader = null;
        XMLEventWriter writer = null;

        try {
            reader = inputFactory.createXMLEventReader(new FileReader(ORIGINAL_PAIN_FILE));
            writer = outputFactory.createXMLEventWriter(new FileWriter(GENERATED_PAIN_FILE));

            // CORRECTED LINE HERE:
            XMLEventFactory eventFactory = XMLEventFactory.newFactory();

            // --- Group Header (GrpHdr) Processing ---
            BigDecimal totalControlSum = BigDecimal.ZERO;
            long totalNumberOfTransactions = 0;

            // Start Document
            writer.add(eventFactory.createStartDocument());

            // Root element (e.g., <Document>)
            writer.add(eventFactory.createStartElement("", "", "Document"));
            writer.add(eventFactory.createNamespace("","urn:iso:std:iso:20022:tech:xsd:pain.001.001.03")); // Example namespace

            // Write updated Group Header (simplified)
            // Note: For a large file generation, you'd calculate totalNbOfTxs and totalCtrlSum *before* writing the GrpHdr,
            // or perform a two-pass approach if modifying an existing file with StAX and these values are in the header.
            // For now, these are placeholders as the calculation happens during batch/transaction generation.
            // A more complete solution would require pre-calculating or buffering.
            writeGroupHeader(writer, eventFactory, "MSG" + UUID.randomUUID().toString().substring(0, 8), 0, BigDecimal.ZERO); // Initial placeholders


            // --- Payment Information (PmtInf) Batch Generation ---
            for (int i = 0; i < numBatches; i++) {
                String batchId = "BATCH-" + UUID.randomUUID().toString();
                long batchTxCount = numTransactionsPerBatch;
                BigDecimal batchCtrlSum = BigDecimal.ZERO;

                writer.add(eventFactory.createStartElement("", "", "PmtInf"));
                writer.add(eventFactory.createStartElement("", "", "PmtInfId"));
                writer.add(eventFactory.createCharacters(batchId));
                writer.add(eventFactory.createEndElement("", "", "PmtInfId"));

                // Add other PmtInf header details (PmtMtd, etc.)
                writer.add(eventFactory.createStartElement("", "", "PmtMtd"));
                writer.add(eventFactory.createCharacters("TRF")); // Example
                writer.add(eventFactory.createEndElement("", "", "PmtMtd"));

                // Update Request Execution Date for each batch
                writer.add(eventFactory.createStartElement("", "", "ReqdExctnDt"));
                writer.add(eventFactory.createCharacters(LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)));
                writer.add(eventFactory.createEndElement("", "", "ReqdExctnDt"));

                // --- Credit Transfer Transaction (CdtTrfTxInf) Generation ---
                for (int j = 0; j < numTransactionsPerBatch; j++) {
                    String transactionId = batchId + "_T" + (j + 1); // BatchnumberT1, T2 format
                    BigDecimal transactionAmount = BigDecimal.valueOf(100.00 + j); // Example amount

                    writer.add(eventFactory.createStartElement("", "", "CdtTrfTxInf"));

                    // PmtId (Payment Identification for transaction)
                    writer.add(eventFactory.createStartElement("", "", "PmtId"));
                    writer.add(eventFactory.createStartElement("", "", "EndToEndId"));
                    writer.add(eventFactory.createCharacters(transactionId));
                    writer.add(eventFactory.createEndElement("", "", "EndToEndId"));
                    writer.add(eventFactory.createEndElement("", "", "PmtId"));

                    // Amt (Amount)
                    writer.add(eventFactory.createStartElement("", "", "Amt"));
                    writer.add(eventFactory.createStartElement("", "", "InstdAmt"));
                    Attribute currencyAttr = eventFactory.createAttribute("Ccy", "EUR"); // Example Currency
                    writer.add(currencyAttr);
                    writer.add(eventFactory.createCharacters(transactionAmount.toPlainString()));
                    writer.add(eventFactory.createEndElement("", "", "InstdAmt"));
                    writer.add(eventFactory.createEndElement("", "", "Amt"));

                    // Add other transaction details (Cdtr, Dbtr, RmtInf etc.)
                    // Example: Creditor Account
                    writer.add(eventFactory.createStartElement("", "", "CdtrAcct"));
                    writer.add(eventFactory.createStartElement("", "", "Id"));
                    writer.add(eventFactory.createStartElement("", "", "IBAN"));
                    writer.add(eventFactory.createCharacters("DE89370400440532013000")); // Example IBAN
                    writer.add(eventFactory.createEndElement("", "", "IBAN"));
                    writer.add(eventFactory.createEndElement("", "", "Id"));
                    writer.add(eventFactory.createEndElement("", "", "CdtrAcct"));

                    writer.add(eventFactory.createEndElement("", "", "CdtTrfTxInf"));

                    batchCtrlSum = batchCtrlSum.add(transactionAmount);
                }

                // Update batch control sum and number of transactions after all transactions are added
                writer.add(eventFactory.createStartElement("", "", "NbOfTxs"));
                writer.add(eventFactory.createCharacters(String.valueOf(batchTxCount)));
                writer.add(eventFactory.createEndElement("", "", "NbOfTxs"));

                writer.add(eventFactory.createStartElement("", "", "CtrlSum"));
                writer.add(eventFactory.createCharacters(batchCtrlSum.toPlainString()));
                writer.add(eventFactory.createEndElement("", "", "CtrlSum"));

                writer.add(eventFactory.createEndElement("", "", "PmtInf"));

                totalNumberOfTransactions += batchTxCount;
                totalControlSum = totalControlSum.add(batchCtrlSum);
            }

            // End Root element
            writer.add(eventFactory.createEndElement("", "", "Document"));
            // End Document
            writer.add(eventFactory.createEndDocument());


            // Important Note for GrpHdr updates with StAX:
            // Since StAX is a streaming API, once you've written the GrpHdr, you can't go back
            // and modify it in the same stream.
            // If you need to update the GrpHdr's total counts AFTER processing all batches,
            // you generally have two main approaches:
            // 1. Calculate totals first, then write the entire file (best for *generating* new files).
            // 2. Read the entire file, buffer the parts, modify the header in memory, then write the whole thing
            //    (less performant for very large files, defeats some StAX benefits).
            // 3. Re-read the output file and modify the header (very inefficient).
            //
            // For pure generation as this example mostly implies, you'd calculate totalControlSum
            // and totalNumberOfTransactions *before* writing the initial GrpHdr.
            // To achieve that in this structure, you'd perform a "dry run" calculation loop first,
            // or collect all PmtInf data into a list, then calculate totals, then write everything.
            // For simplicity in this example, the GrpHdr values are placeholders initially.
            // A more complete solution would store the GrpHdr events or calculate sums beforehand.

        } finally {
            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
        }
    }

    private static void writeGroupHeader(XMLEventWriter writer, XMLEventFactory eventFactory, // Corrected parameter type
                                         String msgId, long totalNbOfTxs, BigDecimal totalCtrlSum) throws XMLStreamException {
        writer.add(eventFactory.createStartElement("", "", "GrpHdr"));

        writer.add(eventFactory.createStartElement("", "", "MsgId"));
        writer.add(eventFactory.createCharacters(msgId));
        writer.add(eventFactory.createEndElement("", "", "MsgId"));

        writer.add(eventFactory.createStartElement("", "", "CreDtTm"));
        writer.add(eventFactory.createCharacters(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
        writer.add(eventFactory.createEndElement("", "", "CreDtTm"));

        writer.add(eventFactory.createStartElement("", "", "NbOfTxs"));
        writer.add(eventFactory.createCharacters(String.valueOf(totalNbOfTxs))); // This value needs to be final before writing
        writer.add(eventFactory.createEndElement("", "", "NbOfTxs"));

        writer.add(eventFactory.createStartElement("", "", "CtrlSum"));
        writer.add(eventFactory.createCharacters(totalCtrlSum.toPlainString())); // This value needs to be final before writing
        writer.add(eventFactory.createEndElement("", "", "CtrlSum"));

        writer.add(eventFactory.createStartElement("", "", "InitgPty"));
        writer.add(eventFactory.createStartElement("", "", "Nm"));
        writer.add(eventFactory.createCharacters("Your Company Name"));
        writer.add(eventFactory.createEndElement("", "", "Nm"));
        writer.add(eventFactory.createEndElement("", "", "InitgPty"));

        writer.add(eventFactory.createEndElement("", "", "GrpHdr"));
    }
}