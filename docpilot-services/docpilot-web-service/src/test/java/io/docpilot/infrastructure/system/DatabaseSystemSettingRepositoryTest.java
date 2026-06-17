package io.docpilot.infrastructure.system;

import io.docpilot.system.repository.SystemSettingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:docpilot_system_setting;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DatabaseSystemSettingRepository.class)
@Sql("classpath:schema-mysql.sql")
class DatabaseSystemSettingRepositoryTest {

    @Autowired
    private SystemSettingRepository repository;

    @Test
    void findsOrCreatesSystemSetting() {
        String first = repository.findOrCreate("system.test.secret", () -> "generated-secret");
        String second = repository.findOrCreate("system.test.secret", () -> "different-secret");

        assertThat(first).isEqualTo("generated-secret");
        assertThat(second).isEqualTo("generated-secret");
        assertThat(repository.findByKey("system.test.secret")).contains("generated-secret");
    }

    @Test
    void savesSystemSetting() {
        repository.save("system.test.flag", "enabled");

        assertThat(repository.findByKey("system.test.flag")).contains("enabled");
    }

}
