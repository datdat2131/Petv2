package vn.soulpets.storage;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Mã hóa map/list phẳng thành chuỗi an toàn (percent-encoding) để lưu vào DB; hỗ trợ lồng nhau. */
public final class Codec {
    private Codec() {}
    private static final String HEX = "0123456789ABCDEF";
    public static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (byte x : s.getBytes(StandardCharsets.UTF_8)) {
            int c = x & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-') b.append((char) c);
            else b.append('%').append(HEX.charAt(c >> 4)).append(HEX.charAt(c & 15));
        }
        return b.toString();
    }
    public static String unesc(String s) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) { o.write(Integer.parseInt(s.substring(i + 1, i + 3), 16)); i += 2; }
            else o.write(c);
        }
        return o.toString(StandardCharsets.UTF_8);
    }
    public static String encodeMap(Map<String, ?> m) {
        StringJoiner j = new StringJoiner("&");
        for (Map.Entry<String, ?> e : m.entrySet()) j.add(esc(e.getKey()) + "=" + esc(String.valueOf(e.getValue())));
        return j.toString();
    }
    public static Map<String, String> decodeMap(String s) {
        Map<String, String> m = new LinkedHashMap<>();
        if (s == null || s.isEmpty()) return m;
        for (String part : s.split("&")) {
            int i = part.indexOf('=');
            if (i < 0) continue;
            m.put(unesc(part.substring(0, i)), unesc(part.substring(i + 1)));
        }
        return m;
    }
    public static String encodeList(Collection<String> c) {
        StringJoiner j = new StringJoiner(",");
        for (String s : c) j.add(esc(s));
        return j.toString();
    }
    public static List<String> decodeList(String s) {
        List<String> l = new ArrayList<>();
        if (s == null || s.isEmpty()) return l;
        for (String p : s.split(",")) l.add(unesc(p));
        return l;
    }
    public static int toInt(String s, int def) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }
    public static long toLong(String s, long def) { try { return Long.parseLong(s); } catch (Exception e) { return def; } }
}
