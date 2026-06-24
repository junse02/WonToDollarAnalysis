package sung.eco_analysis;

import org.junit.jupiter.api.Test;
import sung.eco_analysis.dto.NaverNewsItem;
import sung.eco_analysis.service.StockSentimentService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StockSentimentServiceTest {

    private final StockSentimentService service = new StockSentimentService();

    private NaverNewsItem news(String title) {
        NaverNewsItem item = new NaverNewsItem();
        item.setTitle(title);
        item.setDescription("");
        return item;
    }

    @Test
    void positiveNews_dominant_isBullish() {
        var result = service.analyze(List.of(
                news("삼성전자 신고가 경신"),
                news("호실적에 급등"),
                news("목표가 상향")
        ));
        assertThat(result.label()).isEqualTo("호재 우세");
        assertThat(result.score()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void negativeNews_dominant_isBearish() {
        var result = service.analyze(List.of(
                news("실적 부진에 급락"),
                news("목표가 하향"),
                news("적자 우려 확산")
        ));
        assertThat(result.label()).isEqualTo("악재 우세");
        assertThat(result.score()).isLessThanOrEqualTo(-2);
    }

    @Test
    void mixedOrNeutral_isNeutral() {
        var result = service.analyze(List.of(
                news("삼성전자 신고가"),     // +1
                news("일부 부진 우려"),       // -1
                news("신제품 공개 행사")       // 0
        ));
        assertThat(result.label()).isEqualTo("중립");
    }
}
