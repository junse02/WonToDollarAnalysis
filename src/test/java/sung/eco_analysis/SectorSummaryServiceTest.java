package sung.eco_analysis;

import org.junit.jupiter.api.Test;
import sung.eco_analysis.dto.SectorSummary;
import sung.eco_analysis.dto.StockQuote;
import sung.eco_analysis.service.SectorSummaryService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SectorSummaryServiceTest {

    private final SectorSummaryService service = new SectorSummaryService();

    private StockQuote stock(String name, String sector, Double changePercent, int sentiment, int buzz) {
        Double price = (changePercent == null) ? null : 100.0;
        Double changeAmount = (changePercent == null) ? null : changePercent;  // 부호만 의미
        return new StockQuote(name, name, sector, price, changeAmount, changePercent,
                List.of(), List.of(), "라벨", sentiment, List.of(), buzz);
    }

    @Test
    void summarize_aggregatesPerSector_andSortsByStrength() {
        List<StockQuote> stocks = List.of(
                stock("A", "반도체", 2.0, 1, 1000),
                stock("B", "반도체", 4.0, 1, 500),   // 반도체 평균 +3.0%, buzz 1500, 감성 +2
                stock("C", "금융", 1.0, 0, 300)      // 금융 평균 +1.0%
        );

        List<SectorSummary> result = service.summarize(stocks);

        assertThat(result).hasSize(2);
        // 강세순 정렬 → 반도체(+3.0%)가 금융(+1.0%)보다 앞
        SectorSummary semi = result.get(0);
        assertThat(semi.getSector()).isEqualTo("반도체");
        assertThat(semi.getStockCount()).isEqualTo(2);
        assertThat(semi.getAvgChangePercent()).isEqualTo(3.0);
        assertThat(semi.getNewsBuzz()).isEqualTo(1500);
        assertThat(semi.getSentimentScore()).isEqualTo(2);
        assertThat(semi.getSentimentLabel()).isEqualTo("호재 우세");
        assertThat(result.get(1).getSector()).isEqualTo("금융");
    }

    @Test
    void summarize_sectorWithoutPrices_hasNoStrength_andSortsLast() {
        List<StockQuote> stocks = List.of(
                stock("A", "시세없음", null, 0, 900),   // 시세 없는 종목만
                stock("B", "자동차", -1.0, -2, 100)
        );

        List<SectorSummary> result = service.summarize(stocks);

        // 시세 있는 섹터가 앞, 시세 없는 섹터는 뒤
        assertThat(result.get(0).getSector()).isEqualTo("자동차");
        SectorSummary noPrice = result.get(1);
        assertThat(noPrice.getSector()).isEqualTo("시세없음");
        assertThat(noPrice.isHasPrice()).isFalse();
        assertThat(noPrice.getAvgChangePercent()).isNull();
        assertThat(noPrice.getNewsBuzz()).isEqualTo(900);  // 관심도는 시세와 무관하게 집계
    }
}
