def predict_power(temperature :float, pressure:float, flow:float)->float:
    return (0.2 * temperature + 0.05 * pressure + 1.5 * flow) 
