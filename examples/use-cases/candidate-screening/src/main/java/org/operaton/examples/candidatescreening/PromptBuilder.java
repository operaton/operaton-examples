package org.operaton.examples.candidatescreening;

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

    public String scoreRequest(String candidateName, String position, String applicationText) {
        String system = "You are a recruitment screening assistant. Score the candidate's fit "
            + "for the role on a scale of 0 to 100. Respond ONLY with JSON of the form "
            + "{\"score\": <integer 0-100>, \"reasoning\": <short explanation>}.";
        String user = "Position: " + position + "\nCandidate: " + candidateName
            + "\nApplication:\n" + applicationText;
        return chatRequest(system, user, true);
    }

    public String invitationRequest(String candidateName, String position, String interviewSlot) {
        String system = "You are a recruiting coordinator. Write a warm, concise interview invitation email "
            + "to the candidate. Propose the given interview time slot.";
        String user = "Candidate: " + candidateName + "\nPosition: " + position
            + "\nProposed interview slot: " + interviewSlot;
        return chatRequest(system, user, false);
    }

    public String rejectionRequest(String candidateName, String position) {
        String system = "You are a recruiting coordinator. Write a polite, respectful rejection email "
            + "to the candidate.";
        String user = "Candidate: " + candidateName + "\nPosition: " + position;
        return chatRequest(system, user, false);
    }

    public String recruiterSummaryRequest(String candidateName, String position, int fitScore, String assessment) {
        String system = "You are a recruiting assistant. Write a brief internal summary email to the recruiter "
            + "about a candidate who has been automatically invited.";
        String user = "Candidate: " + candidateName + "\nPosition: " + position
            + "\nFit score: " + fitScore + "\nAssessment: " + assessment;
        return chatRequest(system, user, false);
    }

    private String chatRequest(String system, String user, boolean jsonMode) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", llm.getModel());
        ArrayNode messages = root.putArray("messages");
        ObjectNode sys = messages.addObject();
        sys.put("role", "system");
        sys.put("content", system);
        ObjectNode usr = messages.addObject();
        usr.put("role", "user");
        usr.put("content", user);
        if (jsonMode) {
            root.putObject("response_format").put("type", "json_object");
        }
        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build LLM request JSON", e);
        }
    }
}
