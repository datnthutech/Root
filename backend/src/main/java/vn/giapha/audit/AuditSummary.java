package vn.giapha.audit;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class AuditSummary {
    public record Change(String field, String label, String from, String to) {}

    private static final Map<String, String> PERSON_FIELDS = new LinkedHashMap<>();
    private static final Map<String, String> UNION_FIELDS = new LinkedHashMap<>();
    static {
        PERSON_FIELDS.put("fullName", "Họ tên");
        PERSON_FIELDS.put("nickname", "Tên thường gọi");
        PERSON_FIELDS.put("gender", "Giới tính");
        PERSON_FIELDS.put("birthDate", "Ngày sinh");
        PERSON_FIELDS.put("birthPrecision", "Độ chính xác ngày sinh");
        PERSON_FIELDS.put("birthLunar", "Ngày sinh (âm lịch)");
        PERSON_FIELDS.put("alive", "Còn sống");
        PERSON_FIELDS.put("deathDate", "Ngày mất");
        PERSON_FIELDS.put("deathPrecision", "Độ chính xác ngày mất");
        PERSON_FIELDS.put("deathAnniversaryLunar", "Ngày giỗ (âm lịch)");
        PERSON_FIELDS.put("originPlace", "Nguyên quán");
        PERSON_FIELDS.put("homePlace", "Quê quán");
        PERSON_FIELDS.put("burialPlace", "Nơi an táng");
        PERSON_FIELDS.put("biography", "Sơ yếu lý lịch");
        PERSON_FIELDS.put("birthOrderOverride", "Thứ tự thủ công");
        UNION_FIELDS.put("order", "Thứ tự hôn phối");
        UNION_FIELDS.put("status", "Tình trạng hôn phối");
        UNION_FIELDS.put("startDate", "Ngày bắt đầu");
        UNION_FIELDS.put("endDate", "Ngày kết thúc");
    }

    private AuditSummary() {}

    public static List<Change> changes(String entityType, JsonNode before, JsonNode after) {
        Map<String, String> fields = "PERSON".equals(entityType) ? PERSON_FIELDS : "UNION".equals(entityType) ? UNION_FIELDS : Map.of();
        List<Change> out = new ArrayList<>();
        if (before == null || after == null || fields.isEmpty()) return out;
        fields.forEach((f, label) -> {
            String a = show(f, before.get(f)), b = show(f, after.get(f));
            if (!a.equals(b)) out.add(new Change(f, label, a, b));
        });
        return out;
    }

    public static String summary(String entityType, String action, JsonNode before, JsonNode after, Function<Long, String> nameOf) {
        JsonNode any = after != null ? after : before;
        switch (entityType) {
            case "PERSON": {
                String name = text(after != null && after.hasNonNull("fullName") ? after : before, "fullName");
                switch (action) {
                    case "CREATE": return "Tạo người «" + name + "»";
                    case "UPDATE": return "Sửa hồ sơ «" + name + "»";
                    case "DELETE": return "Xoá «" + name + "»";
                    case "SET_PHOTO": return "Đổi ảnh «" + name + "»";
                    case "REVERT": return "Khôi phục bản cũ «" + name + "»";
                    case "SET_BIRTH_ORDER":
                        return "Đặt thứ tự thủ công «" + name + "»: " + show("birthOrderOverride", before == null ? null : before.get("birthOrderOverride"))
                            + " → " + show("birthOrderOverride", after == null ? null : after.get("birthOrderOverride"));
                    default: return action + " «" + name + "»";
                }
            }
            case "PARENT_CHILD": {
                String rel = personName(any, "parentId", nameOf) + " là cha/mẹ của " + personName(any, "childId", nameOf);
                return ("DELETE".equals(action) ? "Gỡ quan hệ: " : "REVERT".equals(action) ? "Khôi phục quan hệ: " : "Thêm quan hệ: ") + rel;
            }
            case "UNION": {
                String pair = personName(any, "partnerAId", nameOf) + " – " + personName(any, "partnerBId", nameOf);
                return switch (action) {
                    case "DELETE" -> "Gỡ hôn phối: " + pair;
                    case "UPDATE" -> "Sửa hôn phối: " + pair;
                    case "REVERT" -> "Khôi phục hôn phối: " + pair;
                    default -> "Thêm hôn phối: " + pair;
                };
            }
            case "IMPORT": {
                JsonNode a = after == null ? before : after;
                return "Nhập Excel: " + (a == null ? 0 : a.path("persons").asInt()) + " người, "
                    + (a == null ? 0 : a.path("unions").asInt()) + " hôn phối, " + (a == null ? 0 : a.path("links").asInt()) + " quan hệ";
            }
            default: return action + " " + entityType;
        }
    }

    public static boolean revertible(String entityType, String action) {
        return switch (entityType) {
            case "PERSON" -> Set.of("CREATE", "UPDATE", "DELETE", "SET_BIRTH_ORDER").contains(action);
            case "PARENT_CHILD" -> Set.of("CREATE", "DELETE").contains(action);
            case "UNION" -> Set.of("CREATE", "UPDATE", "DELETE").contains(action);
            default -> false;
        };
    }

    private static String text(JsonNode n, String f) { return n == null || n.get(f) == null || n.get(f).isNull() ? "" : n.get(f).asText(); }

    private static String personName(JsonNode n, String f, Function<Long, String> nameOf) {
        if (n == null || !n.hasNonNull(f)) return "?";
        long id = n.get(f).asLong();
        String name = nameOf.apply(id);
        return name == null ? "#" + id : name;
    }

    private static String show(String field, JsonNode v) {
        if (v == null || v.isNull()) return "—";
        if (v.isBoolean()) return v.asBoolean() ? "Có" : "Không";
        String s = v.asText();
        if ("gender".equals(field)) return switch (s) { case "M" -> "Nam"; case "F" -> "Nữ"; default -> "Chưa rõ"; };
        return s.length() > 80 ? s.substring(0, 79) + "…" : s;
    }
}
