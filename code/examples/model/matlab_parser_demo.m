% MATLAB 解析示例文件（用于测试模型函数选择与输入输出自动解析）
% 建议在前端依次选择以下函数观察解析结果：
% 1. calc_with_arguments
% 2. score_with_logical
% 3. fallback_from_comments
% 4. comment_only_no_output
% 5. no_type_hints

function [target, ratio] = calc_with_arguments(flow, pressure)
arguments
    flow (1,1) double
    pressure (1,1) double
end
target = int32(flow > 1.5);
ratio = pressure / 100;
end


function [score, flag] = score_with_logical(v, state)
arguments
    v (1,1) double
    state logical
end
score = v * 0.8;
flag = state;
end


% @Input: speed(FLOAT) - 速度输入
% @Input: mode(STRING) - 控制模式
% @Output: command(INT) - 输出指令
function command = fallback_from_comments(speed, mode)
command = int32(speed > 120);
if strcmp(mode, "safe")
    command = int32(0);
end
end


% @Input: raw(FLOAT) - 原始值
% @Output: normalized(FLOAT) - 归一化值
% 函数头无输出变量，预期触发注释回退（COMMENT_FALLBACK）
function comment_only_no_output(raw)
normalized = raw / 100;
end


% 无 arguments、无注释，输入/输出类型会回落为 STRING
function [a, b, c] = no_type_hints(x, y, z)
a = x;
b = y;
c = z;
end
