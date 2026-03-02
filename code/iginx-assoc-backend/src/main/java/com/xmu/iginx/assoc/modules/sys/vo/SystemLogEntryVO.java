package com.xmu.iginx.assoc.modules.sys.vo;

import lombok.Data;

@Data
public class SystemLogEntryVO {
    private String id;
    private String time;
    private String level;
    private String component;
    private String message;
}
