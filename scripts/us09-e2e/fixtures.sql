-- Validation only: execute exclusively against the new DEVOPS-87 pharmacy_e2e database.
\set ON_ERROR_STOP on
DO $$ BEGIN
    IF current_database() <> 'pharmacy_e2e' THEN
        RAISE EXCEPTION 'US09 fixtures require the isolated pharmacy_e2e database';
    END IF;
END $$;
INSERT INTO pharmacies (id,name,latitude,longitude,status,source,license_number) VALUES
('87000000-0000-4000-8000-000000000001','DEVOPS87 APPROVED proche',31.63,-8,'APPROVED','TEST','DEVOPS87-NEAR'),
('87000000-0000-4000-8000-000000000002','DEVOPS87 PENDING proche',31.63,-8,'PENDING','TEST','DEVOPS87-PENDING'),
('87000000-0000-4000-8000-000000000003','DEVOPS87 APPROVED loin',33.59,-7.62,'APPROVED','TEST','DEVOPS87-FAR')
ON CONFLICT (id) DO NOTHING;

-- Deterministic backend failure; no production code or migration is changed.
CREATE OR REPLACE FUNCTION devops87_fail_test_item() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.medication_name = 'DEVOPS87_FORCE_500' THEN
        RAISE EXCEPTION 'DEVOPS87 controlled failure';
    END IF;
    RETURN NEW;
END $$;
CREATE OR REPLACE TRIGGER devops87_fail_test_item
BEFORE INSERT ON availability_request_items
FOR EACH ROW EXECUTE FUNCTION devops87_fail_test_item();
