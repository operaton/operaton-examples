package org.operaton.examples.bankaccountopening;

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

    public String backgroundCheckRequest(String nationality, String countryOfResidence,
                                         Long annualIncome, String employmentStatus,
                                         String occupation, String sourceOfFunds, String gender) {
        String system = "You are a KYC background-check assistant for a bank. "
            + "Assess the financial crime risk of a bank account applicant based on the provided profile. "
            + "Respond ONLY with JSON of the form "
            + "{\"risk\": \"LOW\"|\"MEDIUM\"|\"HIGH\", \"rationale\": \"<one sentence>\"}. "
            + "HIGH = strong indicators of money-laundering or sanctions risk. "
            + "MEDIUM = some elevated factors requiring review. "
            + "LOW = no significant risk indicators.";
        String user = "Nationality: " + nationality
            + "\nCountry of residence: " + countryOfResidence
            + "\nGender: " + gender
            + "\nAnnual income (EUR): " + annualIncome
            + "\nEmployment status: " + employmentStatus
            + "\nOccupation: " + occupation
            + "\nSource of funds: " + sourceOfFunds;

        ObjectNode root = mapper.createObjectNode();
        root.put("model", llm.getModel());
        root.putObject("response_format").put("type", "json_object");
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);
        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build background-check LLM request", e);
        }
    }
}
