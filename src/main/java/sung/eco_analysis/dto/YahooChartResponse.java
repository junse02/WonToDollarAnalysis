package sung.eco_analysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/** Yahoo Finance chart API(v8) 응답 중 필요한 필드만 매핑. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class YahooChartResponse {

    private Chart chart;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class Chart {
        private List<Result> result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class Result {
        private Meta meta;
        private List<Long> timestamp;          // epoch seconds (일별)
        private Indicators indicators;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class Meta {
        private Double regularMarketPrice;     // 현재가(또는 최근 종가)
        private Double chartPreviousClose;     // 직전 종가
        private String currency;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class Indicators {
        private List<Quote> quote;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class Quote {
        private List<Double> close;            // 일별 종가 (휴장일은 null)
    }
}
