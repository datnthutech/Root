package vn.giapha.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.PostgresTestConfig;
import vn.giapha.clan.ClanMemberRepository;

@SpringBootTest
@Import({PostgresTestConfig.class, TreeFixture.class})
@Transactional
class TreeFixtureTest {
    @Autowired TreeFixture fixture;
    @Autowired ClanMemberRepository members;

    @Test
    void buildsTwelveMembersWithGenerations() {
        var ids = fixture.create();
        var all = members.findByClanId(ids.clan());
        assertThat(all).hasSize(12);
        assertThat(all.stream().filter(m -> m.getPersonId() == ids.g()).findFirst().orElseThrow().getGeneration()).isEqualTo(1);
        assertThat(all.stream().filter(m -> m.getPersonId() == ids.a1()).findFirst().orElseThrow().getGeneration()).isEqualTo(3);
    }
}
