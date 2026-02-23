package com.xmu.iginx.assoc.modules.sys.controller;

import com.xmu.iginx.assoc.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "System Management")
@RestController
@RequestMapping("/api/v1/sys")
public class SystemController {

    @Operation(summary = "Health Check")
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("IGinX Association System is running.");
    }
}
