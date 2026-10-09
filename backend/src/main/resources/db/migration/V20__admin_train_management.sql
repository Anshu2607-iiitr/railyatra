ALTER TABLE trains ADD COLUMN train_type VARCHAR(24) NOT NULL DEFAULT 'EXPRESS';
ALTER TABLE trains ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE trains ADD CONSTRAINT ck_train_type CHECK(train_type IN
 ('EXPRESS','SUPERFAST','MAIL','PASSENGER','INTERCITY','RAJDHANI','SHATABDI','DURONTO','VANDE_BHARAT','SUBURBAN'));
CREATE INDEX idx_trains_active_number ON trains(active, train_number);
CREATE INDEX idx_holds_train_status_expiry ON seat_holds(train_id, status, expires_at);
CREATE TABLE development_seed_runs (seed_key VARCHAR(60) PRIMARY KEY, completed_at TIMESTAMP);
INSERT INTO development_seed_runs(seed_key) VALUES ('foundation-v1'), ('demo-synthetic-v1');
