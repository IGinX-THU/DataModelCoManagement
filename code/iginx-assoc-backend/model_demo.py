# 示例模型脚本
# @Input: speed (Float) - 车速
# @Input: target_temp (Float) - 目标温度
# @Output: power (Float) - 加热功率

def run(speed, target_temp):
    return speed * 0.1 + target_temp * 0.9
