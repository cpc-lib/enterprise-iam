-- Cross-schema read-only views for login identity verification
CREATE OR REPLACE VIEW iam_tenant AS SELECT * FROM iam_identity.iam_tenant;
CREATE OR REPLACE VIEW iam_user AS SELECT * FROM iam_identity.iam_user;
CREATE OR REPLACE VIEW iam_user_identity AS SELECT * FROM iam_identity.iam_user_identity;
