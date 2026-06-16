package sung.eco_analysis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sung.eco_analysis.entity.RateHistory;
import java.time.LocalDateTime;
import java.util.List;

public interface RateHistoryRepository extends JpaRepository<RateHistory, Long> {

    List<RateHistory> findByCurrencyAndRecordedAtAfterOrderByRecordedAt(
            String currency, LocalDateTime after
    );

    boolean existsByCurrencyAndRecordedAtBetween(
            String currency, LocalDateTime start, LocalDateTime end
    );
}