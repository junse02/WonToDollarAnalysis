package sung.eco_analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AnalysisSummary {

    // 달러 강세 압력 지수: -100(원화 강세) ~ +100(달러 강세)
    private int pressureIndex;
    private String pressureLabel;
    // 게이지 위치(0~100, 좌측=약세 우측=강세)
    private int gaugePercent;

    // 분석 적중률
    private int accuracyPercent;   // 키워드 예측 방향과 실제 환율 방향 일치율
    private int matchedCount;      // 일치한 변동일 수
    private int evaluatedCount;    // 예측 가능했던 변동일 수(뉴스+요인 존재)
    private boolean accuracyAvailable;  // 적중률 산출 가능 여부(false면 "산출 중", 0%와 구분)

    private String summaryText;
}
