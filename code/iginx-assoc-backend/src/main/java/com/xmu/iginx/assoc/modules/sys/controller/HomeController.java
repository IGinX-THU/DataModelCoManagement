package com.xmu.iginx.assoc.modules.sys.controller;

import com.xmu.iginx.assoc.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 根路径与健康提示接口。
 */
@Tag(name = "Home")
@RestController
public class HomeController {

    /**
     * 根路径健康提示。
     *
     * @return 服务运行提示
     */
    @Operation(summary = "根路径健康提示")
    @GetMapping("/")
    public Result<String> home() {
        return Result.success("IGinX 关联管理服务运行中");
    }
}
