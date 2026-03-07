# Example Files

This folder contains sample files to help you try the system quickly.
All files are ASCII-only to avoid encoding issues.

## Time Series
- File: examples/timeseries/ts_example.csv
- Columns: timestamp, temperature, pressure, power, flow
- Timestamp format: YYYY-MM-DD HH:mm:ss

Suggested import settings:
- Import type: Time Series (CSV)
- Timestamp column: timestamp
- Storage group example: root.demo
- Mapping example:
  - temperature -> root.demo.temperature
  - pressure -> root.demo.pressure
  - power -> root.demo.power
  - flow -> root.demo.flow

## Structured Data
- File: examples/structured/struct_example.csv
- Columns: device_id, device_name, location, install_date, status

Suggested import settings:
- Import type: Structured (CSV)
- Schema: public
- Table: device_assets
- Auto create table: true
- Conflict strategy: update
- Primary keys: device_id

SQL alternative:
- File: examples/structured/struct_example.sql

## Model Files
- File: examples/model/predict_power.py
  - Contains @Input/@Output tags for schema parsing
- File: examples/model/io_schema.json
  - Can be pasted into the IO Schema field during model upload

## External API Examples
- Directory: examples/external
  - `README.md`: cURL 指令与运行步骤
  - `java/ExternalApiJavaExample.java`: Java 端到端调用示例
  - `python/external_api_example.py`: Python 端到端调用示例

## Association Example
- File: examples/association_rule.json

## Quick Usage Notes
1. Create a data source for IoTDB/IGinX (for time series) and PostgreSQL (for structured).
2. Use the import wizard in Data Editor to upload CSV/SQL files.
3. Use Model Assets to upload the model file and verify parsed schema.
4. Create an association rule by binding input names to time series paths.

