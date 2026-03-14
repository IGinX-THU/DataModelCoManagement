package com.xmu.iginx.assoc.modules.relation.controller;

import com.xmu.iginx.assoc.common.Result;
import com.xmu.iginx.assoc.modules.relation.dto.AssociationRuleCreateRequest;
import com.xmu.iginx.assoc.modules.relation.dto.AssociationRuleUpdateRequest;
import com.xmu.iginx.assoc.modules.relation.dto.RuleStatusRequest;
import com.xmu.iginx.assoc.modules.relation.service.AssociationRuleService;
import com.xmu.iginx.assoc.modules.relation.vo.AssociationRuleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 关联规则管理接口。
 */
@Tag(name = "Association Rules")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rules")
public class AssociationRuleController {

    private final AssociationRuleService associationRuleService;

    /**
     * 创建关联规则。
     *
     * @param request 规则创建参数
     * @return 新建规则 ID
     */
    @Operation(summary = "创建关联规则")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody AssociationRuleCreateRequest request) {
        return Result.success(associationRuleService.createRule(request));
    }

    /**
     * 更新关联规则。
     *
     * @param id 规则 ID
     * @param request 更新参数
     * @return 操作结果
     */
    @Operation(summary = "更新关联规则")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AssociationRuleUpdateRequest request) {
        associationRuleService.updateRule(id, request);
        return Result.success();
    }

    /**
     * 更新规则启用状态。
     *
     * @param id 规则 ID
     * @param request 状态参数
     * @return 操作结果
     */
    @Operation(summary = "更新规则状态")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody RuleStatusRequest request) {
        associationRuleService.updateStatus(id, request.getEnabled());
        return Result.success();
    }

    /**
     * 删除关联规则。
     *
     * @param id 规则 ID
     * @return 操作结果
     */
    @Operation(summary = "删除关联规则")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        associationRuleService.deleteRule(id);
        return Result.success();
    }

    /**
     * 查询规则列表。
     *
     * @return 规则列表
     */
    @Operation(summary = "规则列表")
    @GetMapping
    public Result<List<AssociationRuleVO>> list() {
        return Result.success(associationRuleService.listRules());
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则详情
     */
    @Operation(summary = "规则详情")
    @GetMapping("/{id}")
    public Result<AssociationRuleVO> detail(@PathVariable Long id) {
        return Result.success(associationRuleService.getRule(id));
    }
}
