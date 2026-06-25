package sung.eco_analysis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sung.eco_analysis.entity.StockSnapshot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockSnapshotRepository extends JpaRepository<StockSnapshot, Long> {

    Optional<StockSnapshot> findBySymbolAndSnapshotDate(String symbol, LocalDate snapshotDate);

    List<StockSnapshot> findByEvaluatedFalse();

    List<StockSnapshot> findByEvaluatedTrueAndMatchedIsNotNull();

    // 감성 추이 차트용: 특정 날짜 이후 스냅샷을 날짜 오름차순으로
    List<StockSnapshot> findBySnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(LocalDate from);
}
