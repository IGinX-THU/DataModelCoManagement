package com.xmu.iginx.assoc.modules.sys.service.impl;

import com.xmu.iginx.assoc.modules.sys.service.SystemLogBuffer;
import com.xmu.iginx.assoc.modules.sys.service.SystemLogService;
import com.xmu.iginx.assoc.modules.sys.vo.SystemLogEntryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SystemLogServiceImpl implements SystemLogService {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 2000;

    private final SystemLogBuffer logBuffer;

    @Override
    public List<SystemLogEntryVO> listLogs(Integer limit, String level, String keyword) {
        int safeLimit = normalizeLimit(limit);
        String normalizedLevel = normalizeLevel(level);
        String normalizedKeyword = normalizeKeyword(keyword);
        return logBuffer.snapshot(safeLimit, normalizedLevel, normalizedKeyword);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String normalizeLevel(String level) {
        if (!StringUtils.hasText(level)) {
            return null;
        }
        String normalized = level.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return keyword.trim();
    }
}
