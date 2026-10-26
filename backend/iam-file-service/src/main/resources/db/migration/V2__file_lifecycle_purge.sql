-- Owner: iam-file-service / iam_file
-- SPEC 35 §41/§73: logical file lifecycle needs PURGING/PURGED (physical purge
-- transaction) and FAILED (terminal scan failure after bounded retries).
-- MySQL 8 safe migration: drop the V1 status CHECK and re-add it with the full set.

ALTER TABLE iam_file DROP CHECK ck_file_status;

ALTER TABLE iam_file
    ADD CONSTRAINT ck_file_status CHECK (status IN (
        'VERIFYING','SCANNING','AVAILABLE','QUARANTINED','INFECTED',
        'DELETED','ARCHIVED','PURGING','PURGED','FAILED'));
