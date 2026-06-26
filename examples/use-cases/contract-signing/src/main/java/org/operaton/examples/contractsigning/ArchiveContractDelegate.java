package org.operaton.examples.contractsigning;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component("archiveContractDelegate")
public class ArchiveContractDelegate implements JavaDelegate {
    private final DocumentStore documentStore;

    public ArchiveContractDelegate(DocumentStore documentStore) { this.documentStore = documentStore; }

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String customerSignedKey = (String) execution.getVariable("customerSignedKey");
        String companySignedKey = (String) execution.getVariable("companySignedKey");

        InputStream customerStream = documentStore.get(customerSignedKey);
        byte[] customerPdfBytes = customerStream.readAllBytes();
        customerStream.close();

        InputStream companyStream = documentStore.get(companySignedKey);
        byte[] companyPdfBytes = companyStream.readAllBytes();
        companyStream.close();

        PDDocument customerDoc = Loader.loadPDF(customerPdfBytes);
        PDDocument companyDoc = Loader.loadPDF(companyPdfBytes);
        for (int i = 0; i < companyDoc.getNumberOfPages(); i++) {
            customerDoc.addPage(companyDoc.getPage(i));
        }
        companyDoc.close();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        customerDoc.save(out);
        customerDoc.close();
        byte[] finalPdfBytes = out.toByteArray();

        byte[] hashBytes = MessageDigest.getInstance("SHA-256").digest(finalPdfBytes);
        String documentHash = HexFormat.of().formatHex(hashBytes);

        String finalKey = "contracts/" + execution.getProcessInstanceId() + "/final.pdf";
        documentStore.put(finalKey, new ByteArrayInputStream(finalPdfBytes), finalPdfBytes.length);

        execution.setVariable("finalKey", finalKey);
        execution.setVariable("documentHash", documentHash);
    }
}
