CREATE INDEX idx_train_stops_station_sequence_train ON train_stops (station_id, stop_sequence, train_id);
CREATE INDEX idx_train_schedules_train_dates ON train_schedules (train_id, effective_from, effective_to);
CREATE INDEX idx_train_classes_train_code ON train_classes (train_id, class_code);
