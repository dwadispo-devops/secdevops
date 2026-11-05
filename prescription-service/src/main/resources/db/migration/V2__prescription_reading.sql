-- OCR output is encrypted with the same AES-GCM service as the original image.
ALTER TABLE prescription ADD COLUMN reading_iv BYTEA, ADD COLUMN reading_data BYTEA;
