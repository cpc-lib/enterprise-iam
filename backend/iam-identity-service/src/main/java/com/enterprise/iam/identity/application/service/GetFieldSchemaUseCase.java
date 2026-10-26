package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.domain.model.navigation.FieldDefinition;
import com.enterprise.iam.identity.domain.model.navigation.FieldSchema;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class GetFieldSchemaUseCase {

    private static final Map<String, FieldSchema> RESOURCE_CATALOG = new HashMap<>();

    static {
        RESOURCE_CATALOG.put("USER", new FieldSchema(
                "USER",
                List.of(
                        new FieldDefinition("id", "用户标识", "STRING", false, false, "系统用户唯一标识"),
                        new FieldDefinition("displayName", "显示名称", "STRING", false, false, "用户展示昵称"),
                        new FieldDefinition("phone", "手机号", "STRING", true, true, "手机号（支持脱敏）"),
                        new FieldDefinition("email", "邮箱", "STRING", true, true, "电子邮箱（支持脱敏）"),
                        new FieldDefinition("status", "用户状态", "ENUM", false, false, "用户当前生命周期状态")
                )
        ));

        RESOURCE_CATALOG.put("ROLE", new FieldSchema(
                "ROLE",
                List.of(
                        new FieldDefinition("id", "角色标识", "STRING", false, false, "系统角色唯一标识"),
                        new FieldDefinition("roleCode", "角色代码", "STRING", false, false, "业务角色代码"),
                        new FieldDefinition("roleName", "角色名称", "STRING", false, false, "业务角色显示名称"),
                        new FieldDefinition("status", "角色状态", "ENUM", false, false, "角色当前状态")
                )
        ));

        RESOURCE_CATALOG.put("ORGANIZATION", new FieldSchema(
                "ORGANIZATION",
                List.of(
                        new FieldDefinition("id", "组织标识", "STRING", false, false, "系统组织唯一标识"),
                        new FieldDefinition("orgName", "组织名称", "STRING", false, false, "组织层级全称"),
                        new FieldDefinition("parentId", "上级组织标识", "STRING", false, false, "直属上级组织ID"),
                        new FieldDefinition("status", "组织状态", "ENUM", false, false, "组织启用状态")
                )
        ));

        RESOURCE_CATALOG.put("TEAM", new FieldSchema(
                "TEAM",
                List.of(
                        new FieldDefinition("id", "团队标识", "STRING", false, false, "团队实体唯一标识"),
                        new FieldDefinition("teamName", "团队名称", "STRING", false, false, "团队展示名称"),
                        new FieldDefinition("orgId", "组织标识", "STRING", false, false, "团队所属组织ID"),
                        new FieldDefinition("status", "团队状态", "ENUM", false, false, "团队启用状态")
                )
        ));

        RESOURCE_CATALOG.put("SHARE", new FieldSchema(
                "SHARE",
                List.of(
                        new FieldDefinition("id", "分享标识", "STRING", false, false, "资源分享记录ID"),
                        new FieldDefinition("resourceId", "资源标识", "STRING", false, false, "分享资源唯一标识"),
                        new FieldDefinition("targetType", "分享目标类型", "ENUM", false, false, "USER/TEAM/ROLE/TEAM_ROLE"),
                        new FieldDefinition("targetId", "目标标识", "STRING", false, false, "分享受体目标ID"),
                        new FieldDefinition("status", "分享状态", "ENUM", false, false, "ACTIVE/REVOKED/EXPIRED"),
                        new FieldDefinition("expireTime", "过期时间", "DATETIME", false, false, "分享截止UTC时间")
                )
        ));

        RESOURCE_CATALOG.put("FILE", new FieldSchema(
                "FILE",
                List.of(
                        new FieldDefinition("id", "文件标识", "STRING", false, false, "文件逻辑对象ID"),
                        new FieldDefinition("fileName", "原始文件名", "STRING", false, false, "上传原始文件名"),
                        new FieldDefinition("fileSize", "文件字节大小", "INTEGER", false, false, "文件大小"),
                        new FieldDefinition("sha256", "内容哈希散列", "STRING", false, false, "SHA-256完整性校验值"),
                        new FieldDefinition("status", "文件状态", "ENUM", false, false, "文件流转状态"),
                        new FieldDefinition("scanStatus", "安全扫描状态", "ENUM", false, false, "杀毒/安全扫描结论")
                )
        ));

        RESOURCE_CATALOG.put("JOB", new FieldSchema(
                "JOB",
                List.of(
                        new FieldDefinition("id", "任务标识", "STRING", false, false, "任务定义唯一标识"),
                        new FieldDefinition("jobCode", "任务编码", "STRING", false, false, "后台作业唯一代码"),
                        new FieldDefinition("jobName", "任务名称", "STRING", false, false, "作业业务显示名称"),
                        new FieldDefinition("cronExpression", "Cron表达式", "STRING", false, false, "任务调度CRON时间表达式"),
                        new FieldDefinition("status", "任务状态", "ENUM", false, false, "ACTIVE/PAUSED"),
                        new FieldDefinition("description", "任务描述", "STRING", false, false, "作业执行逻辑描述")
                )
        ));

        RESOURCE_CATALOG.put("APPROVAL", new FieldSchema(
                "APPROVAL",
                List.of(
                        new FieldDefinition("id", "审批单ID", "STRING", false, false, "权限申请单唯一标识"),
                        new FieldDefinition("applicantUserId", "申请人ID", "STRING", false, false, "申请发起人用户ID"),
                        new FieldDefinition("requestType", "申请类型", "ENUM", false, false, "ROLE_ASSIGN/TEAM_ROLE_ASSIGN/RESOURCE_SHARE"),
                        new FieldDefinition("targetId", "目标标识", "STRING", false, false, "申请目标实体ID"),
                        new FieldDefinition("targetName", "目标名称", "STRING", false, false, "申请目标业务名称"),
                        new FieldDefinition("justification", "申请理由", "STRING", false, false, "业务授权合规申请理由"),
                        new FieldDefinition("status", "审批状态", "ENUM", false, false, "PENDING/APPROVED/REJECTED/CANCELLED")
                )
        ));

        RESOURCE_CATALOG.put("ABAC_POLICY", new FieldSchema(
                "ABAC_POLICY",
                List.of(
                        new FieldDefinition("id", "策略ID", "STRING", false, false, "ABAC条件策略唯一标识"),
                        new FieldDefinition("policyCode", "策略编码", "STRING", false, false, "ABAC策略唯一代码"),
                        new FieldDefinition("policyName", "策略名称", "STRING", false, false, "ABAC策略显示名称"),
                        new FieldDefinition("resourceType", "资源类型", "STRING", false, false, "受控资源类型"),
                        new FieldDefinition("action", "操作动作", "STRING", false, false, "受控操作动作"),
                        new FieldDefinition("effect", "策略效果", "ENUM", false, false, "ALLOW/DENY"),
                        new FieldDefinition("conditionJson", "条件表达式", "STRING", false, false, "JSON环境属性与上下文条件规则")
                )
        ));

        RESOURCE_CATALOG.put("SOD_POLICY", new FieldSchema(
                "SOD_POLICY",
                List.of(
                        new FieldDefinition("id", "策略ID", "STRING", false, false, "职责分离策略唯一标识"),
                        new FieldDefinition("policyCode", "策略编码", "STRING", false, false, "互斥策略唯一代码"),
                        new FieldDefinition("policyName", "策略名称", "STRING", false, false, "互斥策略名称"),
                        new FieldDefinition("conflictingRoleIds", "互斥角色集合", "STRING", false, false, "不可兼任的角色ID列表"),
                        new FieldDefinition("maxAllowedRoles", "最大兼任数", "INTEGER", false, false, "互斥集合内允许同时持有的最大角色数"),
                        new FieldDefinition("enforcementMode", "强制模式", "ENUM", false, false, "STATIC/DYNAMIC"),
                        new FieldDefinition("severity", "严重程度", "ENUM", false, false, "CRITICAL/HIGH/MEDIUM")
                )
        ));
    }

    public Optional<FieldSchema> getFieldSchema(String resourceType) {
        if (resourceType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(RESOURCE_CATALOG.get(resourceType.toUpperCase(Locale.ROOT)));
    }
}
