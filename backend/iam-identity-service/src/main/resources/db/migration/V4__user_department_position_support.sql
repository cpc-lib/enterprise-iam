-- Owner: iam-identity-service / iam_identity
-- Support department_id and position_id on iam_user

ALTER TABLE iam_user ADD COLUMN department_id BIGINT UNSIGNED NULL AFTER phone;
ALTER TABLE iam_user ADD COLUMN position_id BIGINT UNSIGNED NULL AFTER department_id;
ALTER TABLE iam_user ADD KEY idx_user_department (tenant_id, department_id, status);
ALTER TABLE iam_user ADD KEY idx_user_position (tenant_id, position_id, status);
