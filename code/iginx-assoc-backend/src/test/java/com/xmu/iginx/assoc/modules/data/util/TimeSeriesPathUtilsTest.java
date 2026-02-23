package com.xmu.iginx.assoc.modules.data.util;

import com.xmu.iginx.assoc.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TimeSeriesPathUtilsTest {

    @Test
    public void resolveRelativePathUnderMount() {
        String resolved = TimeSeriesPathUtils.resolvePathUnderMount("device01", "root.demo", true);
        assertEquals("root.demo.device01", resolved);
    }

    @Test
    public void resolveWithRootPrefix() {
        String resolved = TimeSeriesPathUtils.resolvePathUnderMount("root.demo.device01", "root.demo", true);
        assertEquals("root.demo.device01", resolved);
    }

    @Test
    public void resolveWithoutRootButStartsWithMount() {
        String resolved = TimeSeriesPathUtils.resolvePathUnderMount("demo.device01", "root.demo", true);
        assertEquals("root.demo.device01", resolved);
    }

    @Test
    public void resolveEmptyInputWhenAllowed() {
        String resolved = TimeSeriesPathUtils.resolvePathUnderMount("", "root.demo", true);
        assertEquals("root.demo", resolved);
    }

    @Test
    public void resolveEmptyInputWhenDisallowed() {
        assertThrows(BizException.class, () -> TimeSeriesPathUtils.resolvePathUnderMount("", "root.demo", false));
    }
}
