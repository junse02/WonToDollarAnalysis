package sung.eco_analysis.dto;

import lombok.Getter;

/**
 * 섹터(업종) 단위로 묶은 시장 요약. 페이지 상단에서
 * "어느 섹터가 강세인가(평균 등락률)" · "어느 섹터가 주목받는가(뉴스 관심도)"를 보여준다.
 */
@Getter
public class SectorSummary {

    private final String sector;             // 업종명 (예: 반도체)
    private final int stockCount;            // 이 섹터에 속한 종목 수
    private final Double avgChangePercent;   // 시세 있는 종목들의 평균 등락률(%). 없으면 null
    private final boolean up;                // 평균 등락률 ≥ 0
    private final boolean hasPrice;          // 시세로 강세를 판단할 수 있는지
    private final long newsBuzz;             // 소속 종목 뉴스 검색 건수 합계 (관심도)
    private final int sentimentScore;        // 소속 종목 감성 점수 합계
    private final String sentimentLabel;     // 호재 우세 / 악재 우세 / 중립

    public SectorSummary(String sector, int stockCount, Double avgChangePercent,
                         long newsBuzz, int sentimentScore) {
        this.sector = sector;
        this.stockCount = stockCount;
        this.avgChangePercent = avgChangePercent;
        this.hasPrice = avgChangePercent != null;
        this.up = avgChangePercent != null && avgChangePercent >= 0;
        this.newsBuzz = newsBuzz;
        this.sentimentScore = sentimentScore;
        if (sentimentScore >= 2) this.sentimentLabel = "호재 우세";
        else if (sentimentScore <= -2) this.sentimentLabel = "악재 우세";
        else this.sentimentLabel = "중립";
    }
}
