package org.operaton.examples.contractsigning;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;

@Component("stampSignatureDelegate")
public class StampSignatureDelegate implements JavaDelegate {
    private final DocumentStore documentStore;

    public StampSignatureDelegate(DocumentStore documentStore) { this.documentStore = documentStore; }

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String signerRole = (String) execution.getVariable("signerRole");
        String draftKey = (String) execution.getVariable("draftKey");
        InputStream pdfStream = documentStore.get(draftKey);
        byte[] pdfBytes = pdfStream.readAllBytes();
        pdfStream.close();

        // Load PDF and keep it open (stamping implemented via content stream in real scenario)
        PDDocument document = Loader.loadPDF(pdfBytes);

        // Save document as-is (stamping logic would be added here with proper PDFBox 3.0 API)
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        document.close();
        byte[] signedPdfBytes = out.toByteArray();

        String signedKey = "contracts/" + execution.getProcessInstanceId() + "/" + signerRole + "-signed.pdf";
        documentStore.put(signedKey, new ByteArrayInputStream(signedPdfBytes), signedPdfBytes.length);

        execution.setVariable(signerRole + "SignedKey", signedKey);
        execution.setVariable(signerRole + "SignedAt", Instant.now().toString());
    }
}
