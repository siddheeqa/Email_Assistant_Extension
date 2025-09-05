package com.email.writer.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Service
public class EmailGeneratorService {

    private final WebClient webClient;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public EmailGeneratorService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public String generateEmailReply(EmailRequest emailRequest) {
        try {
            // 1) Build prompt
            String prompt = buildEnhancedPrompt(emailRequest);

            // 2) Prepare request body
            Map<String, Object> requestBody = Map.of(
                    "contents", new Object[]{
                            Map.of(
                                    "role", "user",
                                    "parts", new Object[]{Map.of("text", prompt)}
                            )
                    }
            );

            // 3) Make API call with error handling
            String responseJson = webClient.post()
                    .uri(geminiApiUrl + "?key=" + geminiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 4) Extract text response
            String rawEmail = extractResponseContent(responseJson);

            // 5) Clean up email text
            return postProcessEmail(rawEmail);

        } catch (WebClientResponseException e) {
            return "Gemini API Error: " + e.getResponseBodyAsString();
        } catch (Exception e) {
            return "Unexpected Error: " + e.getMessage();
        }
    }

    // Build Prompt for Gemini
    private String buildEnhancedPrompt(EmailRequest req) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are a professional email writing assistant. ");
        sb.append("Write a clear, concise, and polished email reply. ");
        sb.append("Do NOT include a subject line unless explicitly asked. ");
        sb.append("Ensure proper greeting, structured body, and professional closing. ");
        sb.append("Keep it between 80–150 words. ");
        sb.append("Avoid repetition or generic filler text. ");
        sb.append("Here is the email content: \n");

        if (req.getTone() != null && !req.getTone().isBlank()) {
            sb.append("The tone should be ").append(req.getTone()).append(". ");
        }

        sb.append("Scenario:\n").append(req.getEmailContent());
        return sb.toString();
    }

    // Extract content from Gemini API response
    private String extractResponseContent(String response) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);

            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode parts = candidates.get(0)
                        .path("content")
                        .path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    JsonNode textNode = parts.get(0).path("text");
                    if (!textNode.isMissingNode()) {
                        return textNode.asText();
                    }
                }
            }
            return "No valid response from Gemini.";
        } catch (Exception e) {
            return "Error parsing Gemini response: " + e.getMessage();
        }
    }

    // Post-process email for better formatting
    private String postProcessEmail(String email) {
        if (email == null || email.isBlank()) {
            return "Error: No email generated.";
        }

        email = email.trim();

        // Add greeting if missing
        if (!email.toLowerCase().startsWith("dear") && !email.toLowerCase().startsWith("hi")) {
            email = "Dear [Recipient],\n\n" + email;
        }

        // Add sign-off if missing
        if (!email.toLowerCase().contains("regards") &&
                !email.toLowerCase().contains("sincerely") &&
                !email.toLowerCase().contains("best regards")) {
            email = email + "\n\nBest regards,\n[Your Name]";
        }

        return email;
    }
}
