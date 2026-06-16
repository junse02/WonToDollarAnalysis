package sung.eco_analysis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sung.eco_analysis.entity.DailySnapshot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailySnapshotRepository extends JpaRepository<DailySnapshot, Long> {

    Optional<DailySnapshot> findBySnapshotDate(LocalDate snapshotDate);

    boolean existsBySnapshotDate(LocalDate snapshotDate);

    List<DailySnapshot> findByEvaluatedFalse();

    List<DailySnapshot> findByEvaluatedTrueAndMatchedIsNotNull();
}
