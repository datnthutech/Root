package vn.giapha.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditSummaryTest {
    static final ObjectMapper OM = new ObjectMapper();
    static JsonNode j(String s) throws Exception { return OM.readTree(s); }
    static final Map<Long, String> NAMES = Map.of(1L, "Nguyễn An", 2L, "Nguyễn Một", 3L, "Lê Anh");

    @Test
    void changesListOnlyDifferingFieldsWithLabels() throws Exception {
        var ch = AuditSummary.changes("PERSON",
            j("{\"fullName\":\"A\",\"gender\":\"M\",\"alive\":true,\"nickname\":null,\"birthDate\":\"1950-01-01\"}"),
            j("{\"fullName\":\"B\",\"gender\":\"M\",\"alive\":false,\"nickname\":\"Ba\",\"birthDate\":\"1950-01-01\"}"));
        assertThat(ch).extracting(AuditSummary.Change::field).containsExactly("fullName", "nickname", "alive");
        assertThat(ch.get(0)).isEqualTo(new AuditSummary.Change("fullName", "Họ tên", "A", "B"));
        assertThat(ch.get(1).from()).isEqualTo("—");
        assertThat(ch.get(2).from()).isEqualTo("Có");
        assertThat(ch.get(2).to()).isEqualTo("Không");
    }

    @Test
    void genderIsShownInVietnameseAndLongTextTruncated() throws Exception {
        var ch = AuditSummary.changes("PERSON", j("{\"gender\":\"M\",\"biography\":null}"),
            j("{\"gender\":\"F\",\"biography\":\"" + "x".repeat(200) + "\"}"));
        assertThat(ch.get(0).from()).isEqualTo("Nam");
        assertThat(ch.get(0).to()).isEqualTo("Nữ");
        assertThat(ch.get(1).to()).hasSize(80).endsWith("…");
    }

    @Test
    void nullBeforeOrAfterGivesNoChanges() throws Exception {
        assertThat(AuditSummary.changes("PERSON", null, j("{\"fullName\":\"A\"}"))).isEmpty();
        assertThat(AuditSummary.changes("PARENT_CHILD", j("{}"), j("{\"parentId\":1}"))).isEmpty();
    }

    @Test
    void unionChangesUseLabels() throws Exception {
        var ch = AuditSummary.changes("UNION", j("{\"status\":\"MARRIED\",\"order\":1}"), j("{\"status\":\"DIVORCED\",\"order\":1}"));
        assertThat(ch).hasSize(1);
        assertThat(ch.get(0).label()).isEqualTo("Tình trạng hôn phối");
    }

    @Test
    void summaries() throws Exception {
        assertThat(AuditSummary.summary("PERSON", "CREATE", null, j("{\"fullName\":\"Nguyễn An\"}"), NAMES::get)).isEqualTo("Tạo người «Nguyễn An»");
        assertThat(AuditSummary.summary("PERSON", "UPDATE", j("{\"fullName\":\"A\"}"), j("{\"fullName\":\"B\"}"), NAMES::get)).isEqualTo("Sửa hồ sơ «B»");
        assertThat(AuditSummary.summary("PERSON", "DELETE", j("{\"fullName\":\"A\"}"), null, NAMES::get)).isEqualTo("Xoá «A»");
        assertThat(AuditSummary.summary("PERSON", "SET_BIRTH_ORDER", j("{\"fullName\":\"A\",\"birthOrderOverride\":null}"), j("{\"fullName\":\"A\",\"birthOrderOverride\":3}"), NAMES::get))
            .isEqualTo("Đặt thứ tự thủ công «A»: — → 3");
        assertThat(AuditSummary.summary("PERSON", "SET_PHOTO", j("{\"fullName\":\"A\"}"), j("{\"fullName\":\"A\"}"), NAMES::get)).isEqualTo("Đổi ảnh «A»");
        assertThat(AuditSummary.summary("PERSON", "REVERT", j("{\"fullName\":\"B\"}"), j("{\"fullName\":\"A\"}"), NAMES::get)).isEqualTo("Khôi phục bản cũ «A»");
        assertThat(AuditSummary.summary("PARENT_CHILD", "CREATE", null, j("{\"parentId\":1,\"childId\":2}"), NAMES::get))
            .isEqualTo("Thêm quan hệ: Nguyễn An là cha/mẹ của Nguyễn Một");
        assertThat(AuditSummary.summary("PARENT_CHILD", "DELETE", j("{\"parentId\":1,\"childId\":2}"), null, NAMES::get))
            .isEqualTo("Gỡ quan hệ: Nguyễn An là cha/mẹ của Nguyễn Một");
        assertThat(AuditSummary.summary("UNION", "CREATE", null, j("{\"partnerAId\":1,\"partnerBId\":3}"), NAMES::get)).isEqualTo("Thêm hôn phối: Nguyễn An – Lê Anh");
        assertThat(AuditSummary.summary("UNION", "DELETE", j("{\"partnerAId\":1,\"partnerBId\":3}"), null, NAMES::get)).isEqualTo("Gỡ hôn phối: Nguyễn An – Lê Anh");
    }

    @Test
    void missingNameFallsBackToId() throws Exception {
        assertThat(AuditSummary.summary("PARENT_CHILD", "CREATE", null, j("{\"parentId\":9,\"childId\":2}"), NAMES::get))
            .isEqualTo("Thêm quan hệ: #9 là cha/mẹ của Nguyễn Một");
    }

    @Test
    void revertibleRules() {
        assertThat(AuditSummary.revertible("PERSON", "UPDATE")).isTrue();
        assertThat(AuditSummary.revertible("PERSON", "CREATE")).isTrue();
        assertThat(AuditSummary.revertible("PERSON", "DELETE")).isTrue();
        assertThat(AuditSummary.revertible("PERSON", "SET_BIRTH_ORDER")).isTrue();
        assertThat(AuditSummary.revertible("PERSON", "SET_PHOTO")).isFalse();
        assertThat(AuditSummary.revertible("PERSON", "REVERT")).isFalse();
        assertThat(AuditSummary.revertible("PARENT_CHILD", "CREATE")).isTrue();
        assertThat(AuditSummary.revertible("PARENT_CHILD", "DELETE")).isTrue();
        assertThat(AuditSummary.revertible("UNION", "UPDATE")).isTrue();
        assertThat(AuditSummary.revertible("CLAN", "CREATE")).isFalse();
    }

    @Test
    void importSummary() throws Exception {
        assertThat(AuditSummary.summary("IMPORT", "IMPORT", null, j("{\"persons\":4,\"unions\":1,\"links\":3}"), NAMES::get))
            .isEqualTo("Nhập Excel: 4 người, 1 hôn phối, 3 quan hệ");
    }
}
