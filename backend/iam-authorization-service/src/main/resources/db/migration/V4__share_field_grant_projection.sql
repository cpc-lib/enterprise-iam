CREATE TABLE iam_share_field_grant_projection (
  id BIGINT UNSIGNED NOT NULL,
  tenant_id BIGINT UNSIGNED NOT NULL,
  share_id BIGINT UNSIGNED NOT NULL,
  operation_id BIGINT UNSIGNED NOT NULL,
  field_id BIGINT UNSIGNED NOT NULL,
  readable TINYINT NOT NULL,
  writable TINYINT NOT NULL,
  hidden TINYINT NOT NULL,
  mask_strategy_id BIGINT UNSIGNED NULL,
  projection_epoch BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_share_field_grant (tenant_id, share_id, operation_id, field_id),
  KEY idx_share_field_grant (tenant_id, share_id, operation_id),
  CONSTRAINT ck_field_grant_hidden CHECK (
    (hidden = 1 AND readable = 0 AND writable = 0) OR hidden = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
