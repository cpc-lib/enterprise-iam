-- Owner: iam-sharing-service / iam_sharing
-- Spec V2.0: Folder inheritance ACL and resource collection share scopes.

ALTER TABLE iam_resource_share
    ADD COLUMN scope_type VARCHAR(32) NOT NULL DEFAULT 'INSTANCE';

ALTER TABLE iam_resource_share
    ADD COLUMN folder_path VARCHAR(512) NULL;

ALTER TABLE iam_resource_share
    ADD COLUMN collection_resource_ids_json VARCHAR(2000) NULL;

ALTER TABLE iam_resource_share
    ADD CONSTRAINT ck_iam_share_scope_type CHECK (scope_type IN ('INSTANCE', 'FOLDER_INHERITED', 'COLLECTION'));
