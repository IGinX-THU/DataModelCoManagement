# Example model file for parsing I/O schema
# @Input: temperature(FLOAT) - ambient temperature
# @Input: pressure(FLOAT) - inlet pressure
# @Input: flow(FLOAT) - flow rate
# @Output: power(FLOAT) - predicted power

def predict_power(temperature, pressure, flow):
    return 0.2 * temperature + 0.05 * pressure + 1.5 * flow
