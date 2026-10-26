-- Owner: iam-authorization-service / iam_authorization
-- Spec 47 D-19 (V1.2): expand share ACL projection target types to USER, TEAM, ROLE, TEAM_ROLE.

ALTER TABLE iam_share_acl_projection DROP CHECK ck_share_acl_target,
ADD CONSTRAINT ck_share_acl_target CHECK (target_type IN ('USER','TEAM','ROLE','TEAM_ROLE'));
