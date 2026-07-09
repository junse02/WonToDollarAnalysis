package sung.eco_analysis;

import org.junit.jupiter.api.Test;
import sung.eco_analysis.dto.StockQuote;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StockQuoteTest {

    private StockQuote quote(Double changePercent, int sentimentScore) {
        return new StockQuote("삼성전자", "005930.KS", "반도체", 70000.0, 0.0, changePercent,
                List.of(), List.of(), "라벨", sentimentScore, List.of(), 0);
    }

    @Test
    void abruptMove_whenChangeAtLeast3Percent() {
        assertThat(quote(3.1, 0).isAbruptMove()).isTrue();
        assertThat(quote(-3.0, 0).isAbruptMove()).isTrue();
        assertThat(quote(2.9, 0).isAbruptMove()).isFalse();
        assertThat(quote(null, 0).isAbruptMove()).isFalse();
    }

    @Test
    void badNews_whenSentimentScoreAtMostMinus2() {
        assertThat(quote(0.0, -2).isBadNews()).isTrue();
        assertThat(quote(0.0, -3).isBadNews()).isTrue();
        assertThat(quote(0.0, -1).isBadNews()).isFalse();
        assertThat(quote(0.0, 2).isBadNews()).isFalse();
    }

    @Test
    void alert_whenAbruptMoveOrBadNews() {
        assertThat(quote(5.0, 0).isAlert()).isTrue();    // 급변만
        assertThat(quote(0.5, -2).isAlert()).isTrue();   // 악재만
        assertThat(quote(0.5, 1).isAlert()).isFalse();   // 둘 다 아님
    }
}
