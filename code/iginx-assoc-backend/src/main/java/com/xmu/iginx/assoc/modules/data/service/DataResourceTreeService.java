package com.xmu.iginx.assoc.modules.data.service;

import com.xmu.iginx.assoc.modules.data.vo.DataResourceTreeNodeVO;

import java.util.List;

/**
 * 数据资源树构建服务接口。
 */
public interface DataResourceTreeService {

    /**
     * 构建数据资源树。
     *
     * @return 资源树节点列表
     */
    List<DataResourceTreeNodeVO> buildTree();
}
