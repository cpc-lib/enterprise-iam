# Database & Table Ownership — V1.0

## iam_identity — iam-identity-service
- iam_tenant
- iam_user
- iam_user_identity
- iam_role
- iam_user_role
- sys_outbox_event
- sys_idempotency_record
- sys_message_consume_record

## iam_organization — iam-organization-service
- iam_organization
- iam_team
- iam_team_member
- iam_team_role
- iam_team_member_role
- sys_outbox_event
- sys_idempotency_record
- sys_message_consume_record

## iam_authorization — iam-authorization-service
- iam_application
- iam_service
- iam_resource
- iam_operation
- iam_resource_operation
- iam_permission
- iam_role_permission
- iam_team_role_permission
- iam_api_definition
- iam_api_resource_mapping
- iam_api_security_policy
- iam_resource_data_schema
- iam_data_scope
- iam_role_data_scope
- iam_team_role_data_scope
- iam_role_data_scope_team
- iam_team_role_data_scope_team
- iam_resource_field
- iam_mask_strategy
- iam_field_policy
- iam_role_field_policy
- iam_team_role_field_policy
- iam_condition_policy
- iam_direct_grant
- iam_temporary_grant
- iam_permission_version
- iam_authorization_snapshot
- sys_outbox_event
- sys_idempotency_record
- sys_message_consume_record
- iam_share_security_epoch

## iam_sharing — iam-sharing-service
- iam_resource_share
- iam_resource_share_operation
- iam_resource_share_field
- iam_resource_share_history
- iam_resource_share_basis
- iam_resource_sharing_policy
- iam_share_projection_epoch
- sys_outbox_event
- sys_idempotency_record
- sys_message_consume_record

## iam_auth — iam-auth-service
- iam_login_session
- iam_refresh_token
- iam_user_security_state
- iam_one_time_security_token
- sys_outbox_event
- sys_idempotency_record
- sys_message_consume_record

## iam_audit — iam-audit-service
- iam_login_audit_log
- iam_admin_audit_log
- iam_permission_change_log
- iam_authorization_log
- iam_resource_access_log
- iam_sensitive_field_access_log
- iam_security_event
- iam_infrastructure_operation_log
- sys_outbox_event
- sys_message_consume_record

## iam_job — iam-job-service
- sys_job_business_record
- sys_outbox_event
- sys_message_consume_record

## Business Service Local Database
For services using SHARED Data Scope:
- iam_resource_acl_projection
- iam_acl_projection_checkpoint
- sys_message_consume_record
