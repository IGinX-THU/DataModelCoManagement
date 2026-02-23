package com.xmu.iginx.assoc.modules.relation.service;

import com.xmu.iginx.assoc.modules.relation.dto.AssociationRuleCreateRequest;
import com.xmu.iginx.assoc.modules.relation.dto.AssociationRuleUpdateRequest;
import com.xmu.iginx.assoc.modules.relation.vo.AssociationRuleVO;

import java.util.List;

public interface AssociationRuleService {

    Long createRule(AssociationRuleCreateRequest request);

    void updateRule(Long ruleId, AssociationRuleUpdateRequest request);

    void updateStatus(Long ruleId, boolean enabled);

    void deleteRule(Long ruleId);

    List<AssociationRuleVO> listRules();

    AssociationRuleVO getRule(Long ruleId);
}
