package com.xmu.iginx.assoc.modules.relation.service;

import com.xmu.iginx.assoc.modules.relation.dto.AssociationRuleCreateRequest;
import com.xmu.iginx.assoc.modules.relation.dto.AssociationRuleUpdateRequest;
import com.xmu.iginx.assoc.modules.relation.vo.AssociationRuleVO;

import java.util.List;

/**
 * 关联规则服务接口。
 */
public interface AssociationRuleService {

    /**
     * 创建规则。
     *
     * @param request 创建参数
     * @return 规则 ID
     */
    Long createRule(AssociationRuleCreateRequest request);

    /**
     * 更新规则。
     *
     * @param ruleId 规则 ID
     * @param request 更新参数
     */
    void updateRule(Long ruleId, AssociationRuleUpdateRequest request);

    /**
     * 更新规则启用状态。
     *
     * @param ruleId 规则 ID
     * @param enabled 是否启用
     */
    void updateStatus(Long ruleId, boolean enabled);

    /**
     * 删除规则。
     *
     * @param ruleId 规则 ID
     */
    void deleteRule(Long ruleId);

    /**
     * 获取规则列表。
     *
     * @return 规则列表
     */
    List<AssociationRuleVO> listRules();

    /**
     * 获取规则详情。
     *
     * @param ruleId 规则 ID
     * @return 规则详情
     */
    AssociationRuleVO getRule(Long ruleId);
}
