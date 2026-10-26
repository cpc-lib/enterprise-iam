package com.enterprise.iam.job.interfaces.http;

import com.enterprise.iam.job.application.service.ListJobExecutionsUseCase;
import com.enterprise.iam.job.application.service.ListJobsUseCase;
import com.enterprise.iam.job.application.service.PauseJobUseCase;
import com.enterprise.iam.job.application.service.ResumeJobUseCase;
import com.enterprise.iam.job.application.service.TriggerJobUseCase;
import com.enterprise.iam.job.domain.model.JobDefinition;
import com.enterprise.iam.job.domain.model.JobExecutionLog;
import com.enterprise.iam.job.interfaces.http.dto.ApiResponse;
import com.enterprise.iam.job.interfaces.http.dto.JobDefinitionDto;
import com.enterprise.iam.job.interfaces.http.dto.JobExecutionLogDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/v1/jobs")
@ConditionalOnProperty(prefix = "iam.job", name = "enabled", havingValue = "true")
public class JobManagementController {

    private final ListJobsUseCase listJobsUseCase;
    private final TriggerJobUseCase triggerJobUseCase;
    private final ListJobExecutionsUseCase listJobExecutionsUseCase;
    private final PauseJobUseCase pauseJobUseCase;
    private final ResumeJobUseCase resumeJobUseCase;

    public JobManagementController(
            ListJobsUseCase listJobsUseCase,
            TriggerJobUseCase triggerJobUseCase,
            ListJobExecutionsUseCase listJobExecutionsUseCase,
            PauseJobUseCase pauseJobUseCase,
            ResumeJobUseCase resumeJobUseCase) {
        this.listJobsUseCase = listJobsUseCase;
        this.triggerJobUseCase = triggerJobUseCase;
        this.listJobExecutionsUseCase = listJobExecutionsUseCase;
        this.pauseJobUseCase = pauseJobUseCase;
        this.resumeJobUseCase = resumeJobUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<JobDefinitionDto>>> listJobs() {
        String traceId = UUID.randomUUID().toString();
        List<JobDefinitionDto> dtos = listJobsUseCase.listJobs().stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos, traceId));
    }

    @PostMapping("/{jobCode}/trigger")
    public ResponseEntity<ApiResponse<JobExecutionLogDto>> triggerJob(@PathVariable String jobCode) {
        String traceId = UUID.randomUUID().toString();
        JobExecutionLog executionLog = triggerJobUseCase.trigger(jobCode);
        return ResponseEntity.ok(ApiResponse.ok(toDto(executionLog), traceId));
    }

    @PostMapping("/{jobCode}/pause")
    public ResponseEntity<ApiResponse<JobDefinitionDto>> pauseJob(@PathVariable String jobCode) {
        String traceId = UUID.randomUUID().toString();
        JobDefinition job = pauseJobUseCase.pause(jobCode);
        return ResponseEntity.ok(ApiResponse.ok(toDto(job), traceId));
    }

    @PostMapping("/{jobCode}/resume")
    public ResponseEntity<ApiResponse<JobDefinitionDto>> resumeJob(@PathVariable String jobCode) {
        String traceId = UUID.randomUUID().toString();
        JobDefinition job = resumeJobUseCase.resume(jobCode);
        return ResponseEntity.ok(ApiResponse.ok(toDto(job), traceId));
    }

    @GetMapping("/{jobCode}/executions")
    public ResponseEntity<ApiResponse<List<JobExecutionLogDto>>> listExecutions(
            @PathVariable String jobCode,
            @RequestParam(defaultValue = "50") int limit) {
        String traceId = UUID.randomUUID().toString();
        List<JobExecutionLogDto> dtos = listJobExecutionsUseCase.listExecutions(jobCode, limit).stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos, traceId));
    }

    private JobDefinitionDto toDto(JobDefinition job) {
        return new JobDefinitionDto(
                String.valueOf(job.getId()),
                job.getJobCode(),
                job.getJobName(),
                job.getCronExpression(),
                job.getStatus().name(),
                job.getDescription(),
                job.getCreatedAt(),
                job.getUpdatedAt());
    }

    private JobExecutionLogDto toDto(JobExecutionLog log) {
        return new JobExecutionLogDto(
                String.valueOf(log.getId()),
                log.getJobCode(),
                log.getExecutionId(),
                log.getStatus().name(),
                log.getStartTime(),
                log.getEndTime(),
                log.getItemsProcessed(),
                log.getItemsConverged(),
                log.getErrorMessage(),
                log.getCreatedAt());
    }
}
