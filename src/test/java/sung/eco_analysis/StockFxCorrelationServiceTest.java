package sung.eco_analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sung.eco_analysis.dto.StockCorrelation;
import sung.eco_analysis.dto.StockQuote;
import sung.eco_analysis.entity.RateHistory;
import sung.eco_analysis.service.ExchangeRateService;
import sung.eco_analysis.service.StockFxCorrelationService;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockFxCorrelationServiceTest {

    @Mock ExchangeRateService exchangeRateService;
    @InjectMocks StockFxCorrelationService service;

    @Test
    void pearson_perfectPositiveAndNegative() {
        assertThat(StockFxCorrelationService.pearson(List.of(1.0, 2.0, 3.0), List.of(2.0, 4.0, 6.0)))
                .isCloseTo(1.0, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(StockFxCorrelationService.pearson(List.of(1.0, 2.0, 3.0), List.of(6.0, 4.0, 2.0)))
                .isCloseTo(-1.0, org.assertj.core.api.Assertions.within(1e-9));
    }

    @Test
    void pearson_constantSeries_returnsNull() {
        assertThat(StockFxCorrelationService.pearson(List.of(1.0, 1.0, 1.0), List.of(2.0, 4.0, 6.0)))
                .isNull();
    }

    @Test
    void describe_strengthAndDirection() {
        assertThat(StockFxCorrelationService.describe(0.8)).isEqualTo("강한 동행(+)");
        assertThat(StockFxCorrelationService.describe(-0.5)).isEqualTo("뚜렷한 역행(−)");
        assertThat(StockFxCorrelationService.describe(0.1)).isEqualTo("상관 거의 없음");
    }

    private RateHistory rate(int month, int day, double v) {
        return new RateHistory("USD/KRW", v, LocalDateTime.of(2026, month, day, 12, 0));
    }

    private StockQuote quote(List<String> labels, List<Double> closes) {
        return new StockQuote("삼성전자", "005930.KS", "반도체", closes.get(closes.size() - 1), 0.0, 0.0,
                labels, closes, "라벨", 0, List.of(), 0);
    }

    @Test
    void analyze_matchesByLabel_andComputesCorrelation() {
        when(exchangeRateService.getDailyRecentHistory(90)).thenReturn(List.of(
                rate(6, 10, 1300), rate(6, 11, 1310), rate(6, 12, 1320),
                rate(6, 13, 1330), rate(6, 14, 1340)
        ));
        // 환율과 완벽히 동행하는 종가
        StockQuote q = quote(
                List.of("06/10", "06/11", "06/12", "06/13", "06/14"),
                List.of(70000.0, 71000.0, 72000.0, 73000.0, 74000.0));

        List<StockCorrelation> result = service.analyze(List.of(q));

        assertThat(result).hasSize(1);
        StockCorrelation c = result.get(0);
        assertThat(c.isAvailable()).isTrue();
        assertThat(c.getSampleSize()).isEqualTo(5);
        assertThat(c.getCoefficient()).isCloseTo(1.0, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(c.isPositive()).isTrue();
        assertThat(c.getLabel()).isEqualTo("강한 동행(+)");
    }

    @Test
    void analyze_insufficientSample_marksUnavailable() {
        when(exchangeRateService.getDailyRecentHistory(90)).thenReturn(List.of(
                rate(6, 10, 1300), rate(6, 11, 1310)
        ));
        StockQuote q = quote(List.of("06/10", "06/11"), List.of(70000.0, 71000.0));

        List<StockCorrelation> result = service.analyze(List.of(q));

        assertThat(result.get(0).isAvailable()).isFalse();
        assertThat(result.get(0).getCoefficient()).isNull();
    }
}
