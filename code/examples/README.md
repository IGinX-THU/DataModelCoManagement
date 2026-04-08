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

## Task Chain Demo Pack
- Directory: examples/task_chain_demo
  - Contains a dedicated CSV dataset and three Python models for DAG-like task chain testing
  - Includes a full Chinese walkthrough for:
    - data import
    - model upload
    - association rule creation
    - task chain A -> B -> C setup
    - end-to-end execution

## Task Chain Casebook
- Directory: examples/task_chain_casebook
  - Contains a larger multi-scenario task-chain example suite
  - Covers:
    - linear chains
    - parallel merge chains
    - multi-output branch-and-merge chains
    - structured-data chains
    - validation / failure cases
  - Includes:
    - large CSV datasets
    - dedicated Python models
    - per-scenario Chinese step-by-step guides

## Polyglot Task Chain Casebook
- Directory: examples/task_chain_polyglot_casebook
  - Contains MATLAB and C++ task-chain examples for both time series and structured data
  - Covers:
    - MATLAB time-series branch-and-merge chains
    - MATLAB structured branch-and-merge chains
    - C++ time-series branch-and-merge chains
    - C++ structured branch-and-merge chains
  - Includes:
    - large CSV datasets
    - MATLAB `.m` model files
    - C++ `.cpp` model files
    - per-scenario Chinese step-by-step guides

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


## Task Test Suite
- Directory: examples/model/task_test_suite
  - Contains Python/MATLAB task models and matching ts/rt CSV files
  - Recommended for association rule binding, task execution, export package, and report testing

## Showcase Demo Pack
- Directory: examples/showcase
  - Contains a polished CSV dataset pack for thesis demo and defense
  - Covers time series import, structured import, task comparison, report charts, and export flows
  - See `examples/showcase/README.md` for the recommended demo sequence
