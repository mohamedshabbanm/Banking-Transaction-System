package com.ershak.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonTest {

    record Sample(String name, BigDecimal amount, List<Integer> items, boolean ok) {
    }

    @Test
    @DisplayName("Writes records, keeping money exact")
    void writeRecord() {
        String json = Json.write(new Sample("Omar \"O\"", new BigDecimal("10.50"), List.of(1, 2), true));
        assertEquals("{\"name\":\"Omar \\\"O\\\"\",\"amount\":10.50,\"items\":[1,2],\"ok\":true}", json);
    }

    @Test
    @DisplayName("Parses nested objects and numbers as BigDecimal")
    void parse() {
        Object parsed = Json.parse("{\"a\": [1, 2.50, \"x\"], \"b\": {\"c\": null, \"d\": false}}");
        Map<?, ?> map = (Map<?, ?>) parsed;
        assertEquals(List.of(new BigDecimal("1"), new BigDecimal("2.50"), "x"), map.get("a"));
        assertEquals(Boolean.FALSE, ((Map<?, ?>) map.get("b")).get("d"));
    }

    @Test
    @DisplayName("Escapes < so JSON can't break out into HTML")
    void escapesHtml() {
        assertEquals("\"\\u003cscript>\"", Json.write("<script>"));
    }

    @Test
    @DisplayName("Rejects broken JSON")
    void rejectsBroken() {
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\": }"));
        assertThrows(Json.JsonException.class, () -> Json.parse("[1, 2"));
    }
}
