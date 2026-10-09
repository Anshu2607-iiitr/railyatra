CREATE TABLE seat_holds (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    train_id BIGINT NOT NULL,
    journey_date DATE NOT NULL,
    train_class_id BIGINT NOT NULL,
    quota VARCHAR(30) NOT NULL,
    passenger_count INTEGER NOT NULL,
    inventory_id BIGINT NOT NULL,
    request_key VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_seat_hold_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT fk_seat_hold_train FOREIGN KEY(train_id) REFERENCES trains(id),
    CONSTRAINT fk_seat_hold_class FOREIGN KEY(train_class_id) REFERENCES train_classes(id),
    CONSTRAINT fk_seat_hold_inventory FOREIGN KEY(inventory_id) REFERENCES seat_inventories(id),
    CONSTRAINT uk_seat_hold_user_request UNIQUE(user_id, request_key),
    CONSTRAINT ck_seat_hold_passenger_count CHECK(passenger_count BETWEEN 1 AND 6),
    CONSTRAINT ck_seat_hold_status CHECK(status IN ('ACTIVE','CONSUMED','EXPIRED','RELEASED'))
);
CREATE INDEX idx_seat_holds_expiry ON seat_holds(status, expires_at);
CREATE INDEX idx_seat_holds_user ON seat_holds(user_id, created_at);
