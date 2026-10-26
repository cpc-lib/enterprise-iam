package com.enterprise.iam.fieldpermission;

import com.enterprise.iam.common.core.fieldpolicy.FieldPolicyEnforcer;
import com.enterprise.iam.common.core.fieldpolicy.FieldPolicyMetadata;
import com.enterprise.iam.common.core.fieldpolicy.ObligationFieldPolicyResolver;

import java.util.Map;

/**
 * V2.0 Spring Boot starter component for enforcing field-level read/mask/hide
 * and write permissions on both top-level fields and nested dynamic maps
 * (dot-notation paths such as {@code extAttributes.salary} or {@code extAttributes.*}).
 */
public class DynamicMapFieldEnforcer {

    public Map<String, Object> filterResponse(
            Map<String, Object> source, FieldPolicyMetadata metadata) {
        return FieldPolicyEnforcer.filterResponse(source, metadata);
    }

    public Map<String, Object> filterResponseWithObligations(
            String resourceType,
            Map<String, Object> source,
            Map<String, Object> obligations) {
        FieldPolicyMetadata metadata = ObligationFieldPolicyResolver.fromObligations(resourceType, obligations);
        return FieldPolicyEnforcer.filterResponse(source, metadata);
    }

    public void guardWrite(Map<String, Object> payload, FieldPolicyMetadata metadata) {
        FieldPolicyEnforcer.guardWrite(payload, metadata);
    }

    public void guardWriteWithObligations(
            String resourceType,
            Map<String, Object> payload,
            Map<String, Object> obligations) {
        FieldPolicyMetadata metadata = ObligationFieldPolicyResolver.fromObligations(resourceType, obligations);
        FieldPolicyEnforcer.guardWrite(payload, metadata);
    }
}
