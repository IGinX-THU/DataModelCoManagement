CREATE TABLE public.device_assets (
  device_id INT PRIMARY KEY,
  device_name VARCHAR(64),
  location VARCHAR(64),
  install_date DATE,
  status VARCHAR(16)
);

INSERT INTO public.device_assets (device_id, device_name, location, install_date, status) VALUES
  (1, 'compressor_A', 'plant1', '2024-05-12', 'ACTIVE'),
  (2, 'compressor_B', 'plant1', '2024-05-20', 'ACTIVE'),
  (3, 'pump_C', 'plant2', '2024-06-01', 'MAINT'),
  (4, 'fan_D', 'plant3', '2024-06-10', 'INACTIVE');
