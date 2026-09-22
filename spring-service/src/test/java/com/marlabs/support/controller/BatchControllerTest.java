package com.marlabs.support.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marlabs.support.client.PythonProcessDocumentResult;
import com.marlabs.support.client.PythonServiceClient;
import com.marlabs.support.model.AnswerResponse;
import com.marlabs.support.testsupport.FakePythonServiceClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakePythonServiceClient.Config.class)
class BatchControllerTest {

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

    private MockMultipartFile metadata(String json) {
        return new MockMultipartFile("metadata", "metadata.json", "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void malformedMetadataJsonIsBadRequest() throws Exception {
        mockMvc.perform(multipart("/batches")
                        .file(metadata("{not valid json"))
                        .file(new MockMultipartFile("files", "request-01.txt", "text/plain", "content".getBytes()))
                        .header("X-Caller-Id", "atlas-employee-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST_BODY"));
    }

    @Test
    void duplicateManifestIdsFailsWholeBatch() throws Exception {
        String json = """
                {"batch_id":"demo-01","as_of":"2026-09-21","documents":[
                  {"document_id":"request-01","filename":"a.txt"},
                  {"document_id":"request-01","filename":"b.txt"}
                ]}""";

        mockMvc.perform(multipart("/batches")
                        .file(metadata(json))
                        .file(new MockMultipartFile("files", "a.txt", "text/plain", "content-a".getBytes()))
                        .file(new MockMultipartFile("files", "b.txt", "text/plain", "content-b".getBytes()))
                        .header("X-Caller-Id", "atlas-employee-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_MANIFEST_ID"));
    }

    @Test
    void missingFilePartFailsWholeBatch() throws Exception {
        String json = """
                {"batch_id":"demo-01","as_of":"2026-09-21","documents":[
                  {"document_id":"request-01","filename":"a.txt"},
                  {"document_id":"request-02","filename":"missing.txt"}
                ]}""";

        mockMvc.perform(multipart("/batches")
                        .file(metadata(json))
                        .file(new MockMultipartFile("files", "a.txt", "text/plain", "content-a".getBytes()))
                        .header("X-Caller-Id", "atlas-employee-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MANIFEST_FILE_MISMATCH"));
    }

    @Test
    void extraFilePartNotInManifestFailsWholeBatch() throws Exception {
        String json = """
                {"batch_id":"demo-01","as_of":"2026-09-21","documents":[
                  {"document_id":"request-01","filename":"a.txt"}
                ]}""";

        mockMvc.perform(multipart("/batches")
                        .file(metadata(json))
                        .file(new MockMultipartFile("files", "a.txt", "text/plain", "content-a".getBytes()))
                        .file(new MockMultipartFile("files", "extra.txt", "text/plain", "extra".getBytes()))
                        .header("X-Caller-Id", "atlas-employee-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MANIFEST_FILE_MISMATCH"));
    }

    @Test
    void missingCallerIsUnauthorized() throws Exception {
        String json = """
                {"batch_id":"demo-01","as_of":"2026-09-21","documents":[{"document_id":"request-01","filename":"a.txt"}]}""";

        mockMvc.perform(multipart("/batches")
                        .file(metadata(json))
                        .file(new MockMultipartFile("files", "a.txt", "text/plain", "content-a".getBytes())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mixedCompletedAndFailedResultsPreserveManifestOrderAndDuplicateDetection() throws Exception {
        fake.respondToDocumentsWith(documentId -> {
            if ("request-02".equals(documentId)) {
                throw new com.marlabs.support.exception.PythonServiceTimeoutException("simulated", null);
            }
            return new PythonProcessDocumentResult("COMPLETED", null, null,
                    new AnswerResponse("ANSWERED", "INR 25000", List.of()), true, List.of(), null);
        });

        String json = """
                {"batch_id":"demo-01","as_of":"2026-09-21","documents":[
                  {"document_id":"request-01","filename":"a.txt"},
                  {"document_id":"request-02","filename":"b.txt"},
                  {"document_id":"request-03","filename":"c.txt"}
                ]}""";

        byte[] sameContent = "identical-bytes".getBytes();

        mockMvc.perform(multipart("/batches")
                        .file(metadata(json))
                        .file(new MockMultipartFile("files", "a.txt", "text/plain", sameContent))
                        .file(new MockMultipartFile("files", "b.txt", "text/plain", "other-bytes".getBytes()))
                        .file(new MockMultipartFile("files", "c.txt", "text/plain", sameContent))
                        .header("X-Caller-Id", "atlas-employee-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batch_id").value("demo-01"))
                .andExpect(jsonPath("$.summary.total").value(3))
                .andExpect(jsonPath("$.summary.completed").value(2))
                .andExpect(jsonPath("$.summary.failed").value(1))
                .andExpect(jsonPath("$.results[0].document_id").value("request-01"))
                .andExpect(jsonPath("$.results[0].duplicate_of").value(nullValue()))
                .andExpect(jsonPath("$.results[1].document_id").value("request-02"))
                .andExpect(jsonPath("$.results[1].processing_status").value("FAILED"))
                .andExpect(jsonPath("$.results[1].error.code").value("MODEL_TIMEOUT"))
                .andExpect(jsonPath("$.results[2].document_id").value("request-03"))
                .andExpect(jsonPath("$.results[2].duplicate_of").value("request-01"));
    }
}
