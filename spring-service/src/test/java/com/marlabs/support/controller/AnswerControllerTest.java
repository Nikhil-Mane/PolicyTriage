package com.marlabs.support.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marlabs.support.client.PythonServiceClient;
import com.marlabs.support.model.AnswerResponse;
import com.marlabs.support.model.Citation;
import com.marlabs.support.testsupport.FakePythonServiceClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakePythonServiceClient.Config.class)
class AnswerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PythonServiceClient pythonServiceClient;

    private FakePythonServiceClient fake;

    @BeforeEach
    void setUp() {
        fake = (FakePythonServiceClient) pythonServiceClient;
        fake.reset();
    }

    @Test
    void missingCallerHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(post("/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"as_of\":\"2026-09-21\",\"question\":\"certification cap?\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownCallerHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(post("/answer")
                        .header("X-Caller-Id", "no-such-caller")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"as_of\":\"2026-09-21\",\"question\":\"certification cap?\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedAsOfIsBadRequest() throws Exception {
        mockMvc.perform(post("/answer")
                        .header("X-Caller-Id", "atlas-employee-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"as_of\":\"not-a-date\",\"question\":\"certification cap?\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_AS_OF"));
    }

    @Test
    void emptyQuestionIsBadRequest() throws Exception {
        mockMvc.perform(post("/answer")
                        .header("X-Caller-Id", "atlas-employee-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"as_of\":\"2026-09-21\",\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EMPTY_QUESTION"));
    }

    @Test
    void answeredHappyPath() throws Exception {
        fake.queueAnswer(new AnswerResponse("ANSWERED", "INR 25000", List.of(new Citation("atlas-cert-current", "quoted text"))));

        mockMvc.perform(post("/answer")
                        .header("X-Caller-Id", "atlas-employee-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"as_of\":\"2026-09-21\",\"question\":\"certification cap?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.answer").value("INR 25000"))
                .andExpect(jsonPath("$.citations[0].chunk_id").value("atlas-cert-current"));
    }

    @Test
    void insufficientEvidenceHappyPath() throws Exception {
        fake.queueAnswer(new AnswerResponse("INSUFFICIENT_EVIDENCE", null, List.of()));

        mockMvc.perform(post("/answer")
                        .header("X-Caller-Id", "atlas-employee-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"as_of\":\"2026-09-21\",\"question\":\"gym membership?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INSUFFICIENT_EVIDENCE"))
                .andExpect(jsonPath("$.answer").value(nullValue()));
    }

    @Test
    void conflictHappyPath() throws Exception {
        fake.queueAnswer(new AnswerResponse("CONFLICT", null, List.of(
                new Citation("atlas-home-office-a", "a"), new Citation("atlas-home-office-b", "b"))));

        mockMvc.perform(post("/answer")
                        .header("X-Caller-Id", "atlas-employee-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"as_of\":\"2026-09-21\",\"question\":\"home office allowance?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFLICT"))
                .andExpect(jsonPath("$.citations.length()").value(2));
    }

    @Test
    void pythonTimeoutMapsToGatewayTimeout() throws Exception {
        fake.failNextAnswerWith(FakePythonServiceClient.Failure.TIMEOUT);

        mockMvc.perform(post("/answer")
                        .header("X-Caller-Id", "atlas-employee-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"as_of\":\"2026-09-21\",\"question\":\"certification cap?\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error.code").value("DEPENDENCY_TIMEOUT"));
    }

    @Test
    void pythonUnavailableMapsToBadGateway() throws Exception {
        fake.failNextAnswerWith(FakePythonServiceClient.Failure.UNAVAILABLE);

        mockMvc.perform(post("/answer")
                        .header("X-Caller-Id", "atlas-employee-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"as_of\":\"2026-09-21\",\"question\":\"certification cap?\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("DEPENDENCY_UNAVAILABLE"));
    }
}
