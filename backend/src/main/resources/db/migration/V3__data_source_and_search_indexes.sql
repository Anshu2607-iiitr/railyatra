ALTER TABLE stations ADD COLUMN data_source VARCHAR(20) NOT NULL DEFAULT 'SYNTHETIC';
ALTER TABLE trains ADD COLUMN data_source VARCHAR(20) NOT NULL DEFAULT 'SYNTHETIC';
CREATE INDEX idx_stations_name ON stations (station_name);
CREATE INDEX idx_train_stops_station_train ON train_stops (station_id, train_id);
CREATE INDEX idx_train_schedules_running_days ON train_schedules (runs_on);
