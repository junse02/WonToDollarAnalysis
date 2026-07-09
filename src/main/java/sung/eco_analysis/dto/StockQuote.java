package sung.eco_analysis.dto;

import lombok.Getter;

import java.util.List;

/** 대시보드용 종목 분석 결과 (시세 + 뉴스 감성). */
@Getter
public class StockQuote {

    private final String name;          // 종목명 (예: 삼성전자)
    private final String symbol;        // Yahoo 심볼 (예: 005930.KS)
    private final String sector;        // 업종/섹터 (예: 반도체)
    private final Double price;         // 현재가(원). 조회 실패 시 null
    private final Double changeAmount;  // 전일 대비 변동액
    private final Double changePercent; // 전일 대비 변동률(%)
    private final boolean up;           // 상승 여부
    private final List<String> chartLabels;  // 일자 라벨 (MM/dd)
    private final List<Double> chartData;    // 일별 종가

    // 뉴스 감성
    private final String sentimentLabel;     // 호재 우세 / 악재 우세 / 중립
    private final int sentimentScore;        // 양수=호재, 음수=악재
    private final List<NewsHeadline> headlines;
    private final int newsBuzz;              // 네이버 뉴스 전체 검색 건수 (섹터 관심도 지표)

    public StockQuote(String name, String symbol, String sector, Double price, Double changeAmount,
                      Double changePercent, List<String> chartLabels, List<Double> chartData,
                      String sentimentLabel, int sentimentScore, List<NewsHeadline> headlines, int newsBuzz) {
        this.name = name;
        this.symbol = symbol;
        this.sector = sector;
        this.price = price;
        this.changeAmount = changeAmount;
        this.changePercent = changePercent;
        this.up = changeAmount != null && changeAmount >= 0;
        this.chartLabels = chartLabels;
        this.chartData = chartData;
        this.sentimentLabel = sentimentLabel;
        this.sentimentScore = sentimentScore;
        this.headlines = headlines;
        this.newsBuzz = newsBuzz;
    }

    // 급변으로 강조할 일중 변동률(±%) 임계치
    private static final double ABRUPT_MOVE_PERCENT = 3.0;

    public boolean isAvailable() {
        return price != null;
    }

    /** 전일 대비 ±3% 이상 급변 여부 (주의 강조용). */
    public boolean isAbruptMove() {
        return changePercent != null && Math.abs(changePercent) >= ABRUPT_MOVE_PERCENT;
    }

    /** 뉴스 감성이 '악재 우세'인지 (점수 -2 이하). */
    public boolean isBadNews() {
        return sentimentScore <= -2;
    }

    /** 급변 또는 악재 우세 → 카드 강조 대상. */
    public boolean isAlert() {
        return isAbruptMove() || isBadNews();
    }

    /** 뉴스 헤드라인 (제목 + 링크 + 호재/악재 감성). */
    @Getter
    public static class NewsHeadline {
        private final String title;
        private final String link;
        private final String sentiment;   // 호재 / 악재 / 중립
        public NewsHeadline(String title, String link, String sentiment) {
            this.title = title;
            this.link = link;
            this.sentiment = sentiment;
        }
    }
}
