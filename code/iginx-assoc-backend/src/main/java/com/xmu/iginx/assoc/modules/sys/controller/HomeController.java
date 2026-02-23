package com.xmu.iginx.assoc.modules.sys.controller;

import com.xmu.iginx.assoc.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Home")
@RestController
public class HomeController {

    @Operation(summary = "根路径健康提示")
    @GetMapping("/")
    public Result<String> home() {
        return Result.success("IGinX 关联管理服务运行中");
    }
}
