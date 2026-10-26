package com.enterprise.iam.job.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface JobPersistenceMapper {

    List<JobDefinitionRow> findAllDefinitions();

    JobDefinitionRow findDefinitionByCode(@Param("jobCode") String jobCode);

    int insertDefinition(JobDefinitionRow row);

    int updateDefinition(JobDefinitionRow row);

    int insertExecutionLog(JobExecutionLogRow row);

    int updateExecutionLog(JobExecutionLogRow row);

    List<JobExecutionLogRow> findExecutionLogsByCode(@Param("jobCode") String jobCode, @Param("limit") int limit);

    JobExecutionLogRow findExecutionLogById(@Param("id") long id);
}
