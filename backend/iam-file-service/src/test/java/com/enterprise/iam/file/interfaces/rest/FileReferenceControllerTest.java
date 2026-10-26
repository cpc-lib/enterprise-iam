package com.enterprise.iam.file.interfaces.rest;

import com.enterprise.iam.common.web.json.IamWebJacksonAutoConfiguration;
import com.enterprise.iam.file.application.model.EntityResult;
import com.enterprise.iam.file.application.model.FileAccessResult;
import com.enterprise.iam.file.application.model.FileResult;
import com.enterprise.iam.file.application.service.AuthorizeFileDownloadUseCase;
import com.enterprise.iam.file.application.service.AuthorizeFilePreviewUseCase;
import com.enterprise.iam.file.application.service.CreateFileReferenceUseCase;
import com.enterprise.iam.file.application.service.DeleteLogicalFileUseCase;
import com.enterprise.iam.file.domain.model.FileAccessMode;
import com.enterprise.iam.file.domain.model.FileScanStatus;
import com.enterprise.iam.file.domain.model.FileState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase-03 contract matrix for the file-reference surface: 201 references,
 * 200 download/preview/delete. Entity/audit IDs are decimal strings; versions
 * stay JSON numbers.
 */
class FileReferenceControllerTest {

    private static final Instant EXPIRES_AT = Instant.parse("2030-01-01T00:00:00Z");

    @Mock
    private CreateFileReferenceUseCase createReferenceUseCase;
    @Mock
    private AuthorizeFileDownloadUseCase downloadUseCase;
    @Mock
    private AuthorizeFilePreviewUseCase previewUseCase;
    @Mock
    private DeleteLogicalFileUseCase deleteUseCase;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new IamWebJacksonAutoConfiguration().iamIdAsStringCustomizer().customize(builder);
        builder.configure(objectMapper);
        FileReferenceController controller = new FileReferenceController(
                createReferenceUseCase, downloadUseCase, previewUseCase, deleteUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new FileExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void createReferenceReturns201WithStringIdAndNumericVersion() throws Exception {
        when(createReferenceUseCase.execute(any()))
                .thenReturn(new EntityResult(80001L, 0L, "LINKED"));

        mockMvc.perform(post("/api/v1/files/7001/references")
                        .header("X-Tenant-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "resourceId", 9,
                                "resourceInstanceKey", "invoice-42",
                                "referenceType", "ATTACHMENT",
                                "sortOrder", 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("80001"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.status").value("LINKED"));
    }

    @Test
    void createReferenceWithInvalidTypeReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/files/7001/references")
                        .header("X-Tenant-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "resourceId", 9,
                                "resourceInstanceKey", "invoice-42",
                                "referenceType", "NOT_A_TYPE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadReturns200WithStringAuditId() throws Exception {
        when(downloadUseCase.execute(any())).thenReturn(
                new FileAccessResult(FileAccessMode.PRESIGNED,
                        "https://storage.example/f/7001", EXPIRES_AT, 60001L));

        mockMvc.perform(post("/api/v1/files/7001/download")
                        .header("X-Tenant-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accessBody())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("PRESIGNED"))
                .andExpect(jsonPath("$.data.accessUrl").value("https://storage.example/f/7001"))
                .andExpect(jsonPath("$.data.auditId").value("60001"));
    }

    @Test
    void previewReturns200() throws Exception {
        when(previewUseCase.execute(any())).thenReturn(
                new FileAccessResult(FileAccessMode.SANDBOX_PREVIEW,
                        "https://storage.example/p/7001", EXPIRES_AT, 60002L));

        mockMvc.perform(post("/api/v1/files/7001/preview")
                        .header("X-Tenant-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accessBody())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("SANDBOX_PREVIEW"))
                .andExpect(jsonPath("$.data.auditId").value("60002"));
    }

    @Test
    void deleteReturns200() throws Exception {
        when(deleteUseCase.execute(any())).thenReturn(
                new FileResult(7001L, FileState.DELETED, FileScanStatus.NOT_REQUIRED, 1L));

        mockMvc.perform(post("/api/v1/files/7001/delete")
                        .header("X-Tenant-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "version", 0,
                                "reason", "gdpr-erasure"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").value("7001"))
                .andExpect(jsonPath("$.data.status").value("DELETED"))
                .andExpect(jsonPath("$.data.version").value(1));
    }

    private static Map<String, Object> accessBody() {
        return Map.of(
                "resourceId", 9,
                "resourceInstanceKey", "invoice-42",
                "referenceId", 500,
                "requestedMode", "AUTO");
    }
}
