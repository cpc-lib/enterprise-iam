package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.domain.model.navigation.PageColumn;
import com.enterprise.iam.identity.domain.model.navigation.PageOperation;
import com.enterprise.iam.identity.domain.model.navigation.PageSchema;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GetPageSchemaUseCase {

    private static final Map<String, PageSchema> PAGE_CATALOG = new HashMap<>();

    static {
        PAGE_CATALOG.put("identity-users", new PageSchema(
                "identity-users",
                "用户管理",
                "USER",
                List.of(
                        new PageOperation("btn-create-user", "user:create", "创建用户", "CREATE"),
                        new PageOperation("btn-update-user", "user:update", "编辑用户", "UPDATE"),
                        new PageOperation("btn-disable-user", "user:disable", "停用用户", "DISABLE"),
                        new PageOperation("btn-toggle-status", "user:toggle-status", "启停用户", "UPDATE"),
                        new PageOperation("btn-bind-role", "user:bind-role", "分配角色", "UPDATE"),
                        new PageOperation("btn-reset-password", "user:reset-password", "重置密码", "UPDATE"),
                        new PageOperation("btn-kick-user", "user:kick", "强退会话", "DELETE")
                ),
                List.of(
                        new PageColumn("id", "用户ID", "STRING", true, false),
                        new PageColumn("displayName", "显示名称", "STRING", true, true),
                        new PageColumn("status", "状态", "ENUM", false, true),
                        new PageColumn("createdAt", "创建时间", "DATETIME", true, false)
                )
        ));

        PAGE_CATALOG.put("identity-roles", new PageSchema(
                "identity-roles",
                "角色管理",
                "ROLE",
                List.of(
                        new PageOperation("btn-create-role", "role:create", "创建角色", "CREATE"),
                        new PageOperation("btn-edit-permissions", "role:edit-permissions", "编辑权限", "UPDATE")
                ),
                List.of(
                        new PageColumn("id", "角色ID", "STRING", true, false),
                        new PageColumn("roleCode", "角色标识", "STRING", true, true),
                        new PageColumn("roleName", "角色名称", "STRING", true, true),
                        new PageColumn("status", "状态", "ENUM", false, true),
                        new PageColumn("createdAt", "创建时间", "DATETIME", true, false)
                )
        ));

        PAGE_CATALOG.put("organization-orgs", new PageSchema(
                "organization-orgs",
                "组织机构",
                "ORGANIZATION",
                List.of(
                        new PageOperation("btn-create-org", "org:create", "创建组织", "CREATE")
                ),
                List.of(
                        new PageColumn("id", "组织ID", "STRING", true, false),
                        new PageColumn("orgName", "组织名称", "STRING", true, true),
                        new PageColumn("status", "状态", "ENUM", false, true),
                        new PageColumn("createdAt", "创建时间", "DATETIME", true, false)
                )
        ));

        PAGE_CATALOG.put("organization-teams", new PageSchema(
                "organization-teams",
                "团队管理",
                "TEAM",
                List.of(
                        new PageOperation("btn-create-team", "team:create", "创建团队", "CREATE"),
                        new PageOperation("btn-bind-member", "team:bind-member", "绑定成员", "UPDATE")
                ),
                List.of(
                        new PageColumn("id", "团队ID", "STRING", true, false),
                        new PageColumn("teamName", "团队名称", "STRING", true, true),
                        new PageColumn("status", "状态", "ENUM", false, true),
                        new PageColumn("createdAt", "创建时间", "DATETIME", true, false)
                )
        ));

        PAGE_CATALOG.put("shares", new PageSchema(
                "shares",
                "资源分享",
                "SHARE",
                List.of(
                        new PageOperation("btn-create-share", "share:create", "发起分享", "CREATE"),
                        new PageOperation("btn-revoke-share", "share:revoke", "撤销分享", "REVOKE")
                ),
                List.of(
                        new PageColumn("id", "分享ID", "STRING", true, false),
                        new PageColumn("resourceId", "资源ID", "STRING", true, true),
                        new PageColumn("targetType", "目标类型", "ENUM", false, true),
                        new PageColumn("targetId", "目标ID", "STRING", false, false),
                        new PageColumn("status", "状态", "ENUM", false, true),
                        new PageColumn("expireTime", "过期时间", "DATETIME", true, false)
                )
        ));

        PAGE_CATALOG.put("files", new PageSchema(
                "files",
                "文件管理",
                "FILE",
                List.of(
                        new PageOperation("btn-upload-file", "file:upload", "上传文件", "UPLOAD"),
                        new PageOperation("btn-download-file", "file:download", "下载文件", "DOWNLOAD"),
                        new PageOperation("btn-delete-file", "file:delete", "删除文件", "DELETE")
                ),
                List.of(
                        new PageColumn("id", "文件ID", "STRING", true, false),
                        new PageColumn("fileName", "文件名", "STRING", true, true),
                        new PageColumn("fileSize", "大小", "INTEGER", true, false),
                        new PageColumn("status", "状态", "ENUM", false, true),
                        new PageColumn("createdAt", "创建时间", "DATETIME", true, false)
                )
        ));

        PAGE_CATALOG.put("audit-logs", new PageSchema(
                "audit-logs",
                "审计日志",
                "AUDIT",
                List.of(),
                List.of(
                        new PageColumn("id", "日志ID", "STRING", true, false),
                        new PageColumn("actorId", "操作人ID", "STRING", false, true),
                        new PageColumn("action", "操作动作", "STRING", true, true),
                        new PageColumn("resourceType", "资源类型", "STRING", false, true),
                        new PageColumn("createdAt", "记录时间", "DATETIME", true, false)
                )
        ));

        PAGE_CATALOG.put("system-jobs", new PageSchema(
                "system-jobs",
                "调度中心",
                "JOB",
                List.of(
                        new PageOperation("btn-trigger-job", "job:trigger", "立即执行", "EXECUTE"),
                        new PageOperation("btn-pause-job", "job:pause", "暂停任务", "UPDATE"),
                        new PageOperation("btn-resume-job", "job:resume", "恢复任务", "UPDATE")
                ),
                List.of(
                        new PageColumn("id", "任务ID", "STRING", true, false),
                        new PageColumn("jobCode", "任务代码", "STRING", true, true),
                        new PageColumn("jobName", "任务名称", "STRING", true, true),
                        new PageColumn("cronExpression", "Cron 表达式", "STRING", false, false),
                        new PageColumn("status", "状态", "ENUM", false, true),
                        new PageColumn("description", "描述", "STRING", false, false),
                        new PageColumn("lastExecutionTime", "上次执行时间", "DATETIME", true, false),
                        new PageColumn("nextExecutionTime", "下次执行时间", "DATETIME", true, false)
                )
        ));

        PAGE_CATALOG.put("identity-approvals", new PageSchema(
                "identity-approvals",
                "权限审批流",
                "APPROVAL",
                List.of(
                        new PageOperation("btn-submit-approval", "approval:submit", "发起权限申请", "CREATE"),
                        new PageOperation("btn-approve-request", "approval:approve", "批准申请", "UPDATE"),
                        new PageOperation("btn-reject-request", "approval:reject", "驳回申请", "UPDATE")
                ),
                List.of(
                        new PageColumn("id", "申请单ID", "STRING", true, false),
                        new PageColumn("applicantUserId", "申请人ID", "STRING", false, true),
                        new PageColumn("requestType", "申请类型", "ENUM", false, true),
                        new PageColumn("targetName", "目标名称", "STRING", true, true),
                        new PageColumn("durationHours", "有效时长(h)", "INTEGER", true, false),
                        new PageColumn("status", "审批状态", "ENUM", false, true),
                        new PageColumn("createdAt", "提交时间", "DATETIME", true, false)
                )
        ));

        PAGE_CATALOG.put("authz-abac-policies", new PageSchema(
                "authz-abac-policies",
                "ABAC 条件策略",
                "ABAC_POLICY",
                List.of(
                        new PageOperation("btn-create-abac", "abac:create", "新建条件策略", "CREATE"),
                        new PageOperation("btn-evaluate-abac", "abac:evaluate", "策略仿真推演", "EXECUTE")
                ),
                List.of(
                        new PageColumn("id", "策略ID", "STRING", true, false),
                        new PageColumn("policyCode", "策略编码", "STRING", true, true),
                        new PageColumn("policyName", "策略名称", "STRING", true, true),
                        new PageColumn("resourceType", "资源类型", "STRING", false, true),
                        new PageColumn("action", "动作", "STRING", false, true),
                        new PageColumn("effect", "策略效果", "ENUM", false, true),
                        new PageColumn("status", "状态", "ENUM", false, true)
                )
        ));

        PAGE_CATALOG.put("authz-sod-policies", new PageSchema(
                "authz-sod-policies",
                "职责分离 (SoD)",
                "SOD_POLICY",
                List.of(
                        new PageOperation("btn-create-sod", "sod:create", "新建互斥策略", "CREATE"),
                        new PageOperation("btn-evaluate-sod", "sod:evaluate", "冲突检测验证", "EXECUTE")
                ),
                List.of(
                        new PageColumn("id", "策略ID", "STRING", true, false),
                        new PageColumn("policyCode", "策略编码", "STRING", true, true),
                        new PageColumn("policyName", "策略名称", "STRING", true, true),
                        new PageColumn("enforcementMode", "执行模式", "ENUM", false, true),
                        new PageColumn("severity", "风险等级", "ENUM", false, true),
                        new PageColumn("status", "状态", "ENUM", false, true)
                )
        ));
    }

    public Optional<PageSchema> getPageSchema(String pageKey) {
        return Optional.ofNullable(PAGE_CATALOG.get(pageKey));
    }
}
