package com.enterprise.iam.authorization.infrastructure.persistence.mapper;

import com.enterprise.iam.authorization.infrastructure.persistence.row.ArtifactRow;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

public interface AuthorizationArtifactMapper {

    int insertDataPredicateArtifact(ArtifactRow row);

    ArtifactRow selectActiveDataPredicateArtifact(
            @Param("ref") String ref, @Param("now") Instant now);

    int insertFieldReadPlanArtifact(ArtifactRow row);

    ArtifactRow selectActiveFieldReadPlanArtifact(
            @Param("ref") String ref, @Param("now") Instant now);

    int insertFieldWritePlanArtifact(ArtifactRow row);

    ArtifactRow selectActiveFieldWritePlanArtifact(
            @Param("ref") String ref, @Param("now") Instant now);
}
