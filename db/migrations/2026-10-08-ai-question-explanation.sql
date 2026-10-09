-- One-time additive migration for an existing database. Check the column is absent first.
-- Do not regenerate published exam versions or historical question explanations.
ALTER TABLE question ADD COLUMN explanation TEXT NULL;
