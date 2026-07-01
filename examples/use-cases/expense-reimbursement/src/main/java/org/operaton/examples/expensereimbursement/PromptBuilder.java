package org.operaton.examples.expensereimbursement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {
    private final LlmProperties llm;
    private final ObjectMapper mapper;

    public PromptBuilder(LlmProperties llm, ObjectMapper mapper) {
        this.llm = llm;
        this.mapper = mapper;
    }

    public String receiptAnalysisRequest(String base64Image, String requesterName,
                                         double statedCost, String kind) {
        String system = "You are an expense receipt analysis assistant. "
            + "Analyze the attached receipt image and compare it against the stated expense data. "
            + "Respond ONLY with JSON: "
            + "{\"matchResult\": \"MATCH\"|\"MISMATCH\"|\"UNRELATED\", "
            + "\"extractedName\": \"<payee name from receipt>\", "
            + "\"extractedCost\": <number>, "
            + "\"analysisNotes\": \"<one sentence>\"}. "
            + "MATCH: receipt confirms the stated data. "
            + "MISMATCH: receipt is a valid expense receipt but contradicts stated amount or payee. "
            + "UNRELATED: image cannot be related to the stated expense at all.";
        String userText = "Requester: " + requesterName
            + "\nExpense kind: " + kind
            + "\nStated amount: " + statedCost;

        ObjectNode root = mapper.createObjectNode();
        root.put("model", llm.getModel());
        root.putObject("response_format").put("type", "json_object");
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        ArrayNode contentArray = userMsg.putArray("content");
        contentArray.addObject()
            .put("type", "image_url")
            .putObject("image_url").put("url", "data:image/jpeg;base64," + base64Image);
        contentArray.addObject()
            .put("type", "text")
            .put("text", userText);

        return serialize(root);
    }

    public String approvalEmailRequest(String requesterName, double statedCost,
                                        String kind, String reason, String paymentReference) {
        String system = "You are an expense management assistant. "
            + "Draft a friendly notification email: the expense has been approved for reimbursement. "
            + "Be concise and professional.";
        String user = "Requester: " + requesterName
            + "\nExpense kind: " + kind + ", Amount: " + statedCost
            + "\nReason: " + reason
            + "\nPayment reference: " + paymentReference;
        return chatRequest(system, user);
    }

    public String rejectionEmailRequest(String requesterName, double statedCost,
                                         String kind, String reason) {
        String system = "You are an expense management assistant. "
            + "Draft a polite notification email: the expense reimbursement request has been rejected. "
            + "Be empathetic and professional.";
        String user = "Requester: " + requesterName
            + "\nExpense kind: " + kind + ", Amount: " + statedCost
            + "\nReason for expense: " + reason;
        return chatRequest(system, user);
    }

    private String chatRequest(String system, String user) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", llm.getModel());
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);
        return serialize(root);
    }

    private String serialize(ObjectNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build LLM request JSON", e);
        }
    }
}
