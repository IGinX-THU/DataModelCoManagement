package com.xmu.iginx.assoc.modules.data.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TimeParserTest {

    @Test
    public void parseNumericTimestamps() {
        long seconds = 1_700_000_000L;
        assertEquals(seconds * 1000L, TimeParser.parseToMillis(String.valueOf(seconds), null));

        long millis = 1_700_000_000_000L;
        assertEquals(millis, TimeParser.parseToMillis(String.valueOf(millis), null));

        long micros = 1_700_000_000_000_000L;
        assertEquals(micros / 1000L, TimeParser.parseToMillis(String.valueOf(micros), null));

        long nanos = 1_700_000_000_000_000_000L;
        assertEquals(nanos / 1_000_000L, TimeParser.parseToMillis(String.valueOf(nanos), null));
    }

    @Test
    public void parseFractionalSecondsWithSpace() {
        String value = "2026-02-08 10:00:00.123456";
        long expected = LocalDateTime.of(2026, 2, 8, 10, 0, 0, 123_456_000)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();
        assertEquals(expected, TimeParser.parseToMillis(value, null));
    }
}
