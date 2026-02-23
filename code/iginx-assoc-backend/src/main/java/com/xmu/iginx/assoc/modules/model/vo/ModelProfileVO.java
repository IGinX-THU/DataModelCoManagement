package com.xmu.iginx.assoc.modules.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ModelProfileVO {

    private Long id;
    private String name;
    private String description;
    private String developer;
    private String usageScope;
    private String type;
    private String version;
    private Long fileSize;
    private LocalDateTime uploadTime;
    private Long refCount;
    private LocalDateTime updateTime;
    private List<ModelVersionVO> history;
}
