package sung.eco_analysis.dto;

import lombok.Getter;

import java.util.List;

/** 대시보드용 종목 분석 결과 (시세 + 뉴스 감성). */
@Getter
public class StockQuote {

    private final String name;          // 종목명 (예: 삼성전자)
    private final String symbol;        // Yahoo 심볼 (예: 005930.KS)
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

    public StockQuote(String name, String symbol, Double price, Double changeAmount,
                      Double changePercent, List<String> chartLabels, List<Double> chartData,
                      String sentimentLabel, int sentimentScore, List<NewsHeadline> headlines) {
        this.name = name;
        this.symbol = symbol;
        this.price = price;
        this.changeAmount = changeAmount;
        this.changePercent = changePercent;
        this.up = changeAmount != null && changeAmount >= 0;
        this.chartLabels = chartLabels;
        this.chartData = chartData;
        this.sentimentLabel = sentimentLabel;
        this.sentimentScore = sentimentScore;
        this.headlines = headlines;
    }

    public boolean isAvailable() {
        return price != null;
    }

    /** 뉴스 헤드라인 (제목 + 링크). */
    @Getter
    public static class NewsHeadline {
        private final String title;
        private final String link;
        public NewsHeadline(String title, String link) {
            this.title = title;
            this.link = link;
        }
    }
}
