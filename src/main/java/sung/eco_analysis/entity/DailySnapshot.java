package sung.eco_analysis.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 매일의 분석 스냅샷. snapshotDate 당 1건.
 * 캡처 시점: pressureIndex, predictedUp, rate 저장
 * 사후 평가: 다음 날 환율 확보되면 actualUp, matched, evaluated 채움
 */
@Entity
@Table(name = "daily_snapshot",
        uniqueConstraints = @UniqueConstraint(columnNames = "snapshotDate"))
@Getter
@Setter
@NoArgsConstructor
public class DailySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate snapshotDate;

    private int pressureIndex;

    // 예측 방향: true=달러 강세(상승), false=약세(하락), null=중립(예측 보류)
    private Boolean predictedUp;

    // 스냅샷 시점 환율
    private double rate;

    // ── 사후 평가 결과 ──
    private boolean evaluated = false;
    private Boolean actualUp;   // 실제 다음 날 상승 여부
    private Boolean matched;    // 예측과 실제 일치 여부 (중립이면 null)

    public DailySnapshot(LocalDate snapshotDate, int pressureIndex, Boolean predictedUp, double rate) {
        this.snapshotDate = snapshotDate;
        this.pressureIndex = pressureIndex;
        this.predictedUp = predictedUp;
        this.rate = rate;
    }
}
