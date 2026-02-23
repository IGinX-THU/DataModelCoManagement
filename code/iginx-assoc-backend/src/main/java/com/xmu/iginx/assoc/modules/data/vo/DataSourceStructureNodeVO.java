package com.xmu.iginx.assoc.modules.data.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DataSourceStructureNodeVO {

    private String id;

    private String name;

    private String type;

    private List<DataSourceStructureNodeVO> children = new ArrayList<>();
}
