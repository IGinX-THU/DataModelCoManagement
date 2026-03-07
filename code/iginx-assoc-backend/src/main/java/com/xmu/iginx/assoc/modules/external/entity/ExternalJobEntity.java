package com.xmu.iginx.assoc.modules.external.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "external_job")
public class ExternalJobEntity {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "job_type", nullable = false, length = 32)
    private String jobType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "request_json", columnDefinition = "text")
    private String requestJson;

    @Column(name = "result_json", columnDefinition = "text")
    private String resultJson;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "trace_id", length = 128)
    private String traceId;

    @Column(name = "submit_time")
    private LocalDateTime submitTime;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "finish_time")
    private LocalDateTime finishTime;
}
