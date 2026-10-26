-- Owner: iam-sharing-service / iam_sharing
-- Spec 47 D-19 (V1.2): expand share target types to USER, TEAM, ROLE, TEAM_ROLE.

ALTER TABLE iam_resource_share DROP CHECK ck_share_target,
ADD CONSTRAINT ck_share_target CHECK (target_type IN ('USER','TEAM','ROLE','TEAM_ROLE'));
