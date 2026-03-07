"""
Python 解析示例文件（用于测试模型函数选择与输入输出自动解析）
建议在前端依次选择以下函数观察解析结果：
1. calc_with_annotations
2. infer_from_defaults
3. split_signal_with_comment_types
4. vector_with_return_annotation
5. comment_fallback_only
"""

from typing import Tuple


# @Input: flow(FLOAT) - 入口流量
# @Input: pressure(FLOAT) - 入口压力
# @Output: target(INT) - 目标档位
# @Output: ratio(FLOAT) - 压力配比

def calc_with_annotations(flow: float, pressure: float) -> Tuple[int, float]:
    target = 2 if flow > 1.5 else 1
    ratio = pressure / 100.0
    return target, ratio


# 无类型注解，依赖默认值推断输入类型
# 预期输入类型：temp=FLOAT, count=INT, enabled=BOOLEAN, mode=STRING

def infer_from_defaults(temp=36.5, count=3, enabled=True, mode="auto"):
    return {
        "temp_out": 36.5,
        "enabled_out": True,
        "mode_out": "ok"
    }


# @Output: out1(FLOAT) - 低频分量
# @Output: out2(FLOAT) - 高频分量
# @Output: out3(INT) - 状态码

def split_signal_with_comment_types(signal: float):
    low = signal * 0.2
    high = signal * 0.8
    status = 1
    return low, high, status


# 返回注解为 list[float]，并返回列表字面量
# 预期输出：out1(FLOAT), out2(FLOAT)

def vector_with_return_annotation(x: int, y: int) -> list[float]:
    return [1.0, 2.0]


# @Input: speed(FLOAT) - 当前速度
# @Input: level(INT) - 当前级别
# @Output: score(FLOAT) - 综合评分
# 无 return，预期触发注释回退（COMMENT_FALLBACK）

def comment_fallback_only(speed, level):
    pass
