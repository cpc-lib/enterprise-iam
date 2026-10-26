package com.enterprise.iam.file.interfaces.rest;

import com.enterprise.iam.common.web.json.IamWebJacksonAutoConfiguration;
import com.enterprise.iam.file.application.model.FileResult;
import com.enterprise.iam.file.application.model.SignPartResult;
import com.enterprise.iam.file.application.model.UploadSessionResult;
import com.enterprise.iam.file.application.service.AbortFileUploadSessionUseCase;
import com.enterprise.iam.file.application.service.CompleteFileUploadUseCase;
import com.enterprise.iam.file.application.service.CreateFileUploadSessionUseCase;
import com.enterprise.iam.file.application.service.GetFileUploadSessionUseCase;
import com.enterprise.iam.file.application.service.ReportFileUploadPartUseCase;
import com.enterprise.iam.file.application.service.SignPartUploadUseCase;
import com.enterprise.iam.file.domain.model.FileScanStatus;
import com.enterprise.iam.file.domain.model.FileState;
import com.enterprise.iam.file.domain.model.UploadMode;
import com.enterprise.iam.file.domain.model.UploadSessionStatus;
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

import org.hamcrest.Matchers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase-03 contract status-code / serialization matrix for the upload surface:
 * 201 create, 200 sign/get, 204 part/abort, 202 complete, 400 on invalid body.
 * IDs are decimal strings; partSize/version are JSON numbers.
 */
class FileUploadControllerTest {

    private static final long SNOWFLAKE_UPLOAD_ID = 9_007_199_254_740_993L;
    private static final long SNOWFLAKE_FILE_ID = 9_007_199_254_740_994L;
    private static final String SHA256 = "a".repeat(64);
    private static final Instant EXPIRES_AT = Instant.parse("2030-01-01T00:00:00Z");

    @Mock
    private CreateFileUploadSessionUseCase createUseCase;
    @Mock
    private SignPartUploadUseCase signUseCase;
    @Mock
    private ReportFileUploadPartUseCase reportUseCase;
    @Mock
    private CompleteFileUploadUseCase completeUseCase;
    @Mock
    private GetFileUploadSessionUseCase getUseCase;
    @Mock
    private AbortFileUploadSessionUseCase abortUseCase;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new IamWebJacksonAutoConfiguration().iamIdAsStringCustomizer().customize(builder);
        builder.configure(objectMapper);
        FileUploadController controller = new FileUploadController(
                createUseCase, signUseCase, reportUseCase,
                completeUseCase, getUseCase, abortUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new FileExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void createUploadReturns201WithStringIdsAndNumericPartSize() throws Exception {
        when(createUseCase.execute(any())).thenReturn(sessionResult());

        mockMvc.perform(post("/api/v1/file-uploads")
                        .header("X-Tenant-Id", "7")
                        .header("X-User-Id", "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fileName", "contract.pdf",
                                "fileSize", 1024,
                                "sha256", SHA256,
                                "declaredContentType", "application/pdf",
                                "resourceId", 9,
                                "resourceInstanceKey", "invoice-42"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.uploadId").value(String.valueOf(SNOWFLAKE_UPLOAD_ID)))
                .andExpect(jsonPath("$.data.fileId").value(String.valueOf(SNOWFLAKE_FILE_ID)))
                .andExpect(jsonPath("$.data.mode").value("MULTIPART"))
                .andExpect(jsonPath("$.data.status").value("INIT"))
                .andExpect(jsonPath("$.data.partSize").value(5242880))
                .andExpect(jsonPath("$.data.partCount").value(3))
                .andExpect(jsonPath("$.data.uploadedParts[0]").value(1))
                .andExpect(jsonPath("$.data.version").value(0));
    }

    @Test
    void createUploadWithInvalidSha256Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/file-uploads")
                        .header("X-Tenant-Id", "7")
                        .header("X-User-Id", "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fileName", "contract.pdf",
                                "fileSize", 1024,
                                "sha256", "not-a-hash",
                                "declaredContentType", "application/pdf",
                                "resourceId", 9,
                                "resourceInstanceKey", "invoice-42"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signPartReturns200() throws Exception {
        when(signUseCase.execute(any())).thenReturn(
                new SignPartResult("https://storage.example/part-1", EXPIRES_AT,
                        Map.of("x-amz-signature", "sig")));

        mockMvc.perform(post("/api/v1/file-uploads/7001/parts/1/sign")
                        .header("X-Tenant-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "version", 0,
                                "expectedSize", 5242880,
                                "partSha256", SHA256))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl").value("https://storage.example/part-1"))
                .andExpect(jsonPath("$.data.requiredHeaders.x-amz-signature").value("sig"));
    }

    @Test
    void reportPartReturns204WithNoBody() throws Exception {
        mockMvc.perform(put("/api/v1/file-uploads/7001/parts/1")
                        .header("X-Tenant-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "version", 0,
                                "storageEtag", "etag-1",
                                "partSize", 5242880,
                                "partSha256", SHA256))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(Matchers.emptyOrNullString()));
    }

    @Test
    void completeReturns202WithFileIdStringAndNumericVersion() throws Exception {
        when(completeUseCase.execute(any())).thenReturn(
                new FileResult(SNOWFLAKE_FILE_ID, FileState.VERIFYING, FileScanStatus.PENDING, 0L));

        mockMvc.perform(post("/api/v1/file-uploads/7001/complete")
                        .header("X-Tenant-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "version", 0,
                                "wholeSha256", SHA256,
                                "parts", List.of(Map.of(
                                        "partNumber", 1, "storageEtag", "etag-1"))))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.fileId").value(String.valueOf(SNOWFLAKE_FILE_ID)))
                .andExpect(jsonPath("$.data.status").value("VERIFYING"))
                .andExpect(jsonPath("$.data.scanStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.version").value(0));
    }

    @Test
    void getSessionReturns200() throws Exception {
        when(getUseCase.execute(any())).thenReturn(sessionResult());

        mockMvc.perform(get("/api/v1/file-uploads/7001").header("X-Tenant-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadId").value(String.valueOf(SNOWFLAKE_UPLOAD_ID)));
    }

    @Test
    void abortReturns204WithNoBody() throws Exception {
        mockMvc.perform(delete("/api/v1/file-uploads/7001").header("X-Tenant-Id", "7"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(Matchers.emptyOrNullString()));
    }

    private static UploadSessionResult sessionResult() {
        return new UploadSessionResult(
                UploadMode.MULTIPART,
                SNOWFLAKE_UPLOAD_ID,
                SNOWFLAKE_FILE_ID,
                UploadSessionStatus.INIT,
                5242880L,
                3,
                List.of(1),
                EXPIRES_AT,
                0L);
    }
}
