import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;

public class AppHeaderGenerator {

    public static void main(String[] args) throws Exception {
        generateAppHeaderXML(
            "SENDERBIC",
            "RECEIVERBIC",
            "2502271510350696",
            "2025-02-27T22:57:47+01:00",
            "app_header.xml"
        );
    }

    public static void generateAppHeaderXML(String senderBic, String receiverBic,
                                            String bizMsgIdr, String creationDateTime,
                                            String outputFileName) throws Exception {

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.newDocument();

        // Create env:Envelope root
        Element envelope = doc.createElementNS("urn:swift;xsd:envelope", "env:Envelope");
        envelope.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsi",
                "http://www.w3.org/2001/XMLSchema-instance");
        doc.appendChild(envelope);

        // Create AppHdr
        Element appHdr = doc.createElementNS(
                "urn:iso:std:iso:20022:tech-xsd:head.001.001.02", "AppHdr");
        envelope.appendChild(appHdr);

        // <Fr>
        Element fr = doc.createElement("Fr");
        Element frFld = doc.createElement("FIld");
        Element frInst = doc.createElement("Finlnstnld");
        Element frBic = doc.createElement("BICFI");
        frBic.setTextContent(senderBic);
        frInst.appendChild(frBic);
        frFld.appendChild(frInst);
        fr.appendChild(frFld);
        appHdr.appendChild(fr);

        // <To>
        Element to = doc.createElement("To");
        Element toFld = doc.createElement("Flld");
        Element toInst = doc.createElement("Finlnstnld");
        Element toBic = doc.createElement("BICFI");
        toBic.setTextContent(receiverBic);
        toInst.appendChild(toBic);
        toFld.appendChild(toInst);
        to.appendChild(toFld);
        appHdr.appendChild(to);

        appendTextElement(doc, appHdr, "BizMsgldr", bizMsgIdr);
        appendTextElement(doc, appHdr, "MsgDefldr", "pacs.001.001.03");
        appendTextElement(doc, appHdr, "BizSvc", "swift.cbprplus.02");
        appendTextElement(doc, appHdr, "CreDt", creationDateTime);

        // Output to file
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(outputFileName));
        transformer.transform(source, result);

        System.out.println("App Header written to " + outputFileName);
    }

    private static void appendTextElement(Document doc, Element parent, String name, String value) {
        Element el = doc.createElement(name);
        el.setTextContent(value);
        parent.appendChild(el);
    }
}
