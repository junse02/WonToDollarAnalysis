package sung.eco_analysis.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 종목별 일일 감성 스냅샷. (symbol, snapshotDate) 당 1건.
 * 캡처 시점: sentimentScore, predictedUp, price 저장
 * 사후 평가: 이후 거래일 종가가 확보되면 actualUp, matched, evaluated 채움
 * <p>환율의 {@link DailySnapshot}과 동일한 캡처→평가→적중률 패턴을 종목에 적용한 것.
 * 스냅샷이 매일 종가를 기록하므로, 이 테이블 자체가 종목별 가격 시계열 역할도 한다.
 */
@Entity
@Table(name = "stock_snapshot",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "snapshotDate"}))
@Getter
@Setter
@NoArgsConstructor
public class StockSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;   // Yahoo 심볼 (예: 005930.KS)
    private String name;     // 종목명 (표시용)

    private LocalDate snapshotDate;

    private int sentimentScore;  // 호재(+)/악재(-) 점수

    // 예측 방향: true=호재 우세(상승), false=악재 우세(하락), null=중립(예측 보류)
    private Boolean predictedUp;

    // 스냅샷 시점 종가(원)
    private double price;

    // ── 사후 평가 결과 ──
    private boolean evaluated = false;
    private Boolean actualUp;   // 이후 거래일 실제 상승 여부
    private Boolean matched;    // 예측과 실제 일치 여부 (중립이면 null)

    public StockSnapshot(String symbol, String name, LocalDate snapshotDate,
                         int sentimentScore, Boolean predictedUp, double price) {
        this.symbol = symbol;
        this.name = name;
        this.snapshotDate = snapshotDate;
        this.sentimentScore = sentimentScore;
        this.predictedUp = predictedUp;
        this.price = price;
    }
}
