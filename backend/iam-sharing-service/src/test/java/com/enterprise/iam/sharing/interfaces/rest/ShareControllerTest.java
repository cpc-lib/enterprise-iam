package com.enterprise.iam.sharing.interfaces.rest;

import com.enterprise.iam.common.web.json.IamWebJacksonAutoConfiguration;
import com.enterprise.iam.sharing.application.command.CreateShareCommand;
import com.enterprise.iam.sharing.application.command.ReshareCommand;
import com.enterprise.iam.sharing.application.exception.ShareDomainInvariantException;
import com.enterprise.iam.sharing.application.model.CreateShareResult;
import com.enterprise.iam.sharing.application.service.CreateShareUseCase;
import com.enterprise.iam.sharing.application.service.GetShareUseCase;
import com.enterprise.iam.sharing.application.service.ReshareUseCase;
import com.enterprise.iam.sharing.application.service.RevokeShareUseCase;
import com.enterprise.iam.sharing.domain.model.Share;
import com.enterprise.iam.sharing.domain.model.ShareStatus;
import com.enterprise.iam.sharing.domain.model.ShareTargetType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShareControllerTest {

    private static final long SNOWFLAKE_ID = 9_007_199_254_740_993L;

    @Mock
    private CreateShareUseCase createShareUseCase;
    @Mock
    private RevokeShareUseCase revokeShareUseCase;
    @Mock
    private ReshareUseCase reshareUseCase;
    @Mock
    private GetShareUseCase getShareUseCase;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new IamWebJacksonAutoConfiguration().iamIdAsStringCustomizer().customize(builder);
        builder.configure(objectMapper);
        ShareController controller = new ShareController(
                createShareUseCase, revokeShareUseCase, reshareUseCase, getShareUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ShareExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void createUserSharePersistsNestedTargetAndFieldsAndSerializesContractShape() throws Exception {
        when(createShareUseCase.create(any()))
                .thenReturn(new CreateShareResult(SNOWFLAKE_ID, ShareStatus.ACTIVE, 0L));

        Map<String, Object> body = baseBody(ShareTargetType.USER.name(), 11L);
        body.put("fields", List.of(fieldGrant(21L, 301L, true, false, false, 800L)));

        mockMvc.perform(post("/api/v1/shares")
                        .header("X-Tenant-Id", "7")
                        .header("X-User-Id", "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CREATED"))
                .andExpect(jsonPath("$.data.shareId").value(String.valueOf(SNOWFLAKE_ID)))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.projectionEpoch").value(1));

        ArgumentCaptor<CreateShareCommand> captor = ArgumentCaptor.forClass(CreateShareCommand.class);
        verify(createShareUseCase).create(captor.capture());
        CreateShareCommand command = captor.getValue();
        assertThat(command.tenantId()).isEqualTo(7L);
        assertThat(command.creatorUserId()).isEqualTo(13L);
        assertThat(command.resourceId()).isEqualTo(9L);
        assertThat(command.targetType()).isEqualTo(ShareTargetType.USER);
        assertThat(command.targetId()).isEqualTo(11L);
        assertThat(command.parentShareId()).isNull();
        assertThat(command.operationIds()).containsExactly(21L);
        assertThat(command.fields()).hasSize(1);
        assertThat(command.fields().get(0).fieldId()).isEqualTo(301L);
        assertThat(command.fields().get(0).maskStrategyId()).isEqualTo(800L);
    }

    @Test
    void createTeamShareAcceptsHiddenAndMaskedFields() throws Exception {
        when(createShareUseCase.create(any()))
                .thenReturn(new CreateShareResult(100L, ShareStatus.ACTIVE, 0L));

        Map<String, Object> body = baseBody(ShareTargetType.TEAM.name(), 22L);
        body.put("fields", List.of(
                fieldGrant(21L, 301L, false, false, true, null),
                fieldGrant(21L, 302L, true, true, false, 801L)));

        mockMvc.perform(post("/api/v1/shares")
                        .header("X-Tenant-Id", "7")
                        .header("X-User-Id", "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shareId").value("100"));
    }

    @Test
    void emptyFieldListIsValid() throws Exception {
        when(createShareUseCase.create(any()))
                .thenReturn(new CreateShareResult(100L, ShareStatus.ACTIVE, 0L));

        Map<String, Object> body = baseBody(ShareTargetType.USER.name(), 11L);
        body.put("fields", List.of());

        mockMvc.perform(post("/api/v1/shares")
                        .header("X-Tenant-Id", "7")
                        .header("X-User-Id", "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    void missingTargetIsBadRequest() throws Exception {
        Map<String, Object> body = baseBody(ShareTargetType.USER.name(), 11L);
        body.remove("target");

        mockMvc.perform(post("/api/v1/shares")
                        .header("X-Tenant-Id", "7")
                        .header("X-User-Id", "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moreThanHundredOperationsIsBadRequest() throws Exception {
        List<Long> operations = new ArrayList<>();
        for (long i = 1; i <= 101; i++) {
            operations.add(i);
        }
        Map<String, Object> body = baseBody(ShareTargetType.USER.name(), 11L);
        body.put("operationIds", operations);

        mockMvc.perform(post("/api/v1/shares")
                        .header("X-Tenant-Id", "7")
                        .header("X-User-Id", "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moreThanThousandFieldsIsBadRequest() throws Exception {
        List<Map<String, Object>> fields = new ArrayList<>();
        for (long i = 1; i <= 1001; i++) {
            fields.add(fieldGrant(21L, i, true, false, false, null));
        }
        Map<String, Object> body = baseBody(ShareTargetType.USER.name(), 11L);
        body.put("fields", fields);

        mockMvc.perform(post("/api/v1/shares")
                        .header("X-Tenant-Id", "7")
                        .header("X-User-Id", "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hiddenReadableInvariantReturns422() throws Exception {
        when(createShareUseCase.create(any()))
                .thenThrow(new ShareDomainInvariantException(
                        "hidden field grant cannot be readable or writable"));

        Map<String, Object> body = baseBody(ShareTargetType.USER.name(), 11L);
        body.put("fields", List.of(fieldGrant(21L, 301L, true, false, true, null)));

        mockMvc.perform(post("/api/v1/shares")
                        .header("X-Tenant-Id", "7")
                        .header("X-User-Id", "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("IAM_SHARE_INVARIANT_VIOLATION"));
    }

    @Test
    void reshareCarriesParentVersionAndPathParentShareId() throws Exception {
        when(reshareUseCase.reshare(any()))
                .thenReturn(new CreateShareResult(200L, ShareStatus.ACTIVE, 0L));

        Map<String, Object> body = baseBody(ShareTargetType.USER.name(), 11L);
        body.put("fields", List.of());
        body.put("parentVersion", 5);

        mockMvc.perform(post("/api/v1/shares/555/reshare")
                        .header("X-Tenant-Id", "7")
                        .header("X-User-Id", "13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shareId").value("200"));

        ArgumentCaptor<ReshareCommand> captor = ArgumentCaptor.forClass(ReshareCommand.class);
        verify(reshareUseCase).reshare(captor.capture());
        assertThat(captor.getValue().parentVersion()).isEqualTo(5L);
        assertThat(captor.getValue().command().parentShareId()).isEqualTo(555L);
    }

    @Test
    void revokeReturnsOkWithNumericVersion() throws Exception {
        when(revokeShareUseCase.revoke(any()))
                .thenReturn(new CreateShareResult(555L, ShareStatus.REVOKED, 1L));

        Map<String, Object> body = Map.of("version", 0, "reason", "owner-left");

        mockMvc.perform(post("/api/v1/shares/555/revoke")
                        .header("X-Tenant-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareId").value("555"))
                .andExpect(jsonPath("$.data.status").value("REVOKED"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.projectionEpoch").value(2));
    }

    @Test
    void getReturnsOk() throws Exception {
        Instant now = Instant.parse("2026-09-24T10:00:00Z");
        when(getShareUseCase.getShare(7L, 555L)).thenReturn(new Share(
                555L, 7L, 9L, "invoice-42", ShareTargetType.USER, 11L, 13L,
                null, 555L, 0, ShareStatus.ACTIVE,
                now.minusSeconds(60), now.plusSeconds(3600), false, 1,
                null, null, null, 0L, now, now));

        mockMvc.perform(get("/api/v1/shares/555").header("X-Tenant-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareId").value("555"))
                .andExpect(jsonPath("$.data.version").value(0));
    }

    private static Map<String, Object> baseBody(String targetType, long targetId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resourceId", 9);
        body.put("resourceInstanceKey", "invoice-42");
        body.put("target", Map.of("type", targetType, "id", targetId));
        body.put("operationIds", List.of(21));
        body.put("fields", List.of());
        body.put("expireAt", "2030-01-01T00:00:00Z");
        body.put("canReshare", false);
        return body;
    }

    private static Map<String, Object> fieldGrant(
            long operationId, long fieldId, boolean readable, boolean writable,
            boolean hidden, Long maskStrategyId) {
        Map<String, Object> grant = new LinkedHashMap<>();
        grant.put("operationId", operationId);
        grant.put("fieldId", fieldId);
        grant.put("readable", readable);
        grant.put("writable", writable);
        grant.put("hidden", hidden);
        if (maskStrategyId != null) {
            grant.put("maskStrategyId", maskStrategyId);
        }
        return grant;
    }
}
