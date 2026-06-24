package sung.eco_analysis;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import sung.eco_analysis.controller.WebController;
import sung.eco_analysis.dto.NaverNewsItem;
import sung.eco_analysis.entity.RateHistory;
import sung.eco_analysis.service.ExchangeRateService;
import sung.eco_analysis.service.KeywordAnalysisService;
import sung.eco_analysis.service.NewsArchiveService;
import sung.eco_analysis.service.SnapshotService;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class WebControllerTest {

    private final ExchangeRateService exchangeRateService = mock(ExchangeRateService.class);
    private final NewsArchiveService newsArchiveService = mock(NewsArchiveService.class);
    private final SnapshotService snapshotService = mock(SnapshotService.class);
    // 분석 서비스는 외부 의존성이 없으므로 실제 객체 사용
    private final KeywordAnalysisService keywordAnalysisService = new KeywordAnalysisService();

    private final WebController controller = new WebController(
            exchangeRateService, newsArchiveService, keywordAnalysisService, snapshotService);

    private NaverNewsItem news(String title) {
        NaverNewsItem item = new NaverNewsItem();
        item.setTitle(title);
        item.setDescription("내용");
        item.setPubDate("Wed, 17 Jun 2026 00:00:00 +0900");
        item.setLink("http://example.com");
        return item;
    }

    // 환율 API 실패 시 마지막 저장값으로 폴백하고 경고를 노출한다
    @Test
    void rateFailure_fallsBackToStored_andWarns() {
        when(exchangeRateService.fetchCurrentUsdKrw()).thenReturn(null);
        when(exchangeRateService.getLatestStoredRate())
                .thenReturn(new RateHistory("USD/KRW", 1500.0, LocalDateTime.of(2026, 6, 16, 12, 0)));
        when(exchangeRateService.getRecentHistory(30)).thenReturn(List.of());
        when(newsArchiveService.getLatestNews(100)).thenReturn(List.of(news("달러 환율 상승")));
        when(snapshotService.getAccuracyStats()).thenReturn(new int[]{0, 0});

        Model model = new ExtendedModelMap();
        String view = controller.index(model);

        assertThat(view).isEqualTo("index");
        assertThat(model.getAttribute("rateStale")).isEqualTo(true);
        assertThat(model.getAttribute("currentRate")).isEqualTo(1500.0);
        List<String> warnings = (List<String>) model.getAttribute("warnings");
        assertThat(warnings).isNotEmpty();
        assertThat(warnings.get(0)).contains("마지막 저장값");
    }

    // 뉴스 API 실패(빈 결과) 시 경고를 노출하고 페이지는 정상 렌더된다
    @Test
    void newsFailure_warnsButRenders() {
        when(exchangeRateService.fetchCurrentUsdKrw()).thenReturn(1490.0);
        when(exchangeRateService.getRecentHistory(30)).thenReturn(List.of());
        when(newsArchiveService.getLatestNews(100)).thenReturn(List.of());
        when(snapshotService.getAccuracyStats()).thenReturn(new int[]{0, 0});

        Model model = new ExtendedModelMap();
        String view = controller.index(model);

        assertThat(view).isEqualTo("index");
        assertThat(model.getAttribute("newsCount")).isEqualTo(0);
        List<String> warnings = (List<String>) model.getAttribute("warnings");
        assertThat(warnings).anyMatch(w -> w.contains("네이버 뉴스"));
    }

    // 정상 응답이면 경고가 없고 rateStale=false
    @Test
    void allHealthy_noWarnings() {
        when(exchangeRateService.fetchCurrentUsdKrw()).thenReturn(1490.0);
        when(exchangeRateService.getRecentHistory(30)).thenReturn(List.of());
        when(newsArchiveService.getLatestNews(100)).thenReturn(List.of(news("환율 분석")));
        when(snapshotService.getAccuracyStats()).thenReturn(new int[]{0, 0});

        Model model = new ExtendedModelMap();
        controller.index(model);

        assertThat(model.getAttribute("rateStale")).isEqualTo(false);
        List<String> warnings = (List<String>) model.getAttribute("warnings");
        assertThat(warnings).isEmpty();
    }
}
