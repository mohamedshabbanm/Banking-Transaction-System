package com.ershak.web;

import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A small JSON writer and parser written from scratch, so the project has no JSON library dependency.
 * <p>
 * Writing supports: null, strings, numbers, booleans, enums, maps, lists, arrays, Optional and Java records.
 * Parsing returns: {@code Map<String,Object>}, {@code List<Object>}, String, BigDecimal, Boolean or null.
 * Numbers are parsed as BigDecimal so money keeps its exact value.
 */
public final class Json {

    private Json() {
    }

    // ------------------------------------------------------------------ writing

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            string(sb, s);
        } else if (v instanceof BigDecimal d) {
            sb.append(d.toPlainString());
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Enum<?> e) {
            string(sb, e.name());
        } else if (v instanceof Optional<?> o) {
            write(sb, o.orElse(null));
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                string(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object item : it) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, item);
            }
            sb.append(']');
        } else if (v.getClass().isArray()) {
            sb.append('[');
            for (int i = 0; i < Array.getLength(v); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                write(sb, Array.get(v, i));
            }
            sb.append(']');
        } else if (v instanceof Record r) {
            sb.append('{');
            boolean first = true;
            for (RecordComponent rc : r.getClass().getRecordComponents()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                string(sb, rc.getName());
                sb.append(':');
                try {
                    rc.getAccessor().setAccessible(true);
                    write(sb, rc.getAccessor().invoke(r));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Can't read " + rc.getName(), e);
                }
            }
            sb.append('}');
        } else {
            string(sb, v.toString());
        }
    }

    private static void string(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<' -> sb.append("\\u003c");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ parsing

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object value = p.value();
        p.skipWhitespace();
        if (!p.atEnd()) {
            throw new JsonException("Unexpected text after JSON value");
        }
        return value;
    }

    public static class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }
    }

    private static final class Parser {
        private final String s;
        private int i;
        private int depth;

        Parser(String s) {
            this.s = s == null ? "" : s;
        }

        boolean atEnd() {
            return i >= s.length();
        }

        void skipWhitespace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        Object value() {
            if (atEnd()) {
                throw new JsonException("Unexpected end of JSON");
            }
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            enter();
            Map<String, Object> map = new LinkedHashMap<>();
            i++;
            skipWhitespace();
            if (peek('}')) {
                i++;
                depth--;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw new JsonException("Expected a field name");
                }
                String key = string();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                map.put(key, value());
                skipWhitespace();
                if (peek(',')) {
                    i++;
                } else {
                    expect('}');
                    depth--;
                    return map;
                }
            }
        }

        private List<Object> array() {
            enter();
            List<Object> list = new ArrayList<>();
            i++;
            skipWhitespace();
            if (peek(']')) {
                i++;
                depth--;
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(value());
                skipWhitespace();
                if (peek(',')) {
                    i++;
                } else {
                    expect(']');
                    depth--;
                    return list;
                }
            }
        }

        private String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new JsonException("Unterminated string");
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw new JsonException("Bad escape");
                    }
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"', '\\', '/' -> sb.append(e);
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            if (i + 4 > s.length()) {
                                throw new JsonException("Bad unicode escape");
                            }
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> throw new JsonException("Bad escape");
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private BigDecimal number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            if (start == i) {
                throw new JsonException("Unexpected character '" + s.charAt(i) + "'");
            }
            try {
                return new BigDecimal(s.substring(start, i));
            } catch (NumberFormatException e) {
                throw new JsonException("Bad number");
            }
        }

        private Object literal(String word, Object value) {
            if (!s.startsWith(word, i)) {
                throw new JsonException("Unexpected token");
            }
            i += word.length();
            return value;
        }

        private boolean peek(char c) {
            return i < s.length() && s.charAt(i) == c;
        }

        private void expect(char c) {
            if (!peek(c)) {
                throw new JsonException("Expected '" + c + "'");
            }
            i++;
        }

        private void enter() {
            if (++depth > 32) {
                throw new JsonException("JSON nested too deeply");
            }
        }
    }
}
