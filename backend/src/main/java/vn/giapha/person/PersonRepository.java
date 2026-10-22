package vn.giapha.person;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {
    Optional<Person> findByIdAndDeletedAtIsNull(Long id);
}
