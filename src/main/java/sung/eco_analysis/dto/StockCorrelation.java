package sung.eco_analysis.dto;

import lombok.Getter;

/** 종목 종가와 원/달러 환율의 상관 분석 결과. */
@Getter
public class StockCorrelation {

    private final String name;
    private final String symbol;
    private final boolean available;      // 상관 산출에 충분한 표본이 있었는지
    private final Double coefficient;      // 피어슨 상관계수 (-1 ~ +1), 표본 부족 시 null
    private final int sampleSize;          // 짝지어진 거래일 수
    private final String label;            // "강한 동행" / "약한 역행" / "상관 거의 없음" 등
    private final boolean positive;        // 동행(+) 여부 (표시 색상용)

    public StockCorrelation(String name, String symbol, boolean available,
                            Double coefficient, int sampleSize, String label, boolean positive) {
        this.name = name;
        this.symbol = symbol;
        this.available = available;
        this.coefficient = coefficient;
        this.sampleSize = sampleSize;
        this.label = label;
        this.positive = positive;
    }
}
