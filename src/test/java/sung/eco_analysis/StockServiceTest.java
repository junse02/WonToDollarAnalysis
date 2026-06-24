package sung.eco_analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import sung.eco_analysis.dto.NaverNewsItem;
import sung.eco_analysis.dto.StockQuote;
import sung.eco_analysis.dto.YahooChartResponse;
import sung.eco_analysis.service.NaverNewsService;
import sung.eco_analysis.service.StockSentimentService;
import sung.eco_analysis.service.StockService;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock RestTemplate restTemplate;
    @Mock NaverNewsService naverNewsService;

    // 감성 서비스는 외부 의존성이 없어 실제 객체 사용
    private final StockSentimentService sentimentService = new StockSentimentService();

    private YahooChartResponse chartResponse(double price, double prevClose) {
        YahooChartResponse resp = new YahooChartResponse();
        YahooChartResponse.Chart chart = new YahooChartResponse.Chart();
        YahooChartResponse.Result res = new YahooChartResponse.Result();
        YahooChartResponse.Meta meta = new YahooChartResponse.Meta();
        meta.setRegularMarketPrice(price);
        meta.setChartPreviousClose(prevClose);
        res.setMeta(meta);
        res.setTimestamp(List.of(1718000000L, 1718086400L));
        YahooChartResponse.Indicators ind = new YahooChartResponse.Indicators();
        YahooChartResponse.Quote q = new YahooChartResponse.Quote();
        q.setClose(Arrays.asList(prevClose, price));
        ind.setQuote(List.of(q));
        res.setIndicators(ind);
        chart.setResult(List.of(res));
        resp.setChart(chart);
        return resp;
    }

    private NaverNewsItem news(String title) {
        NaverNewsItem item = new NaverNewsItem();
        item.setTitle(title);
        item.setDescription("");
        item.setLink("http://example.com");
        return item;
    }

    @Test
    void getTopStocks_buildsQuotesFromYahooAndNews() {
        StockService stockService = new StockService(restTemplate, naverNewsService, sentimentService);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(YahooChartResponse.class)))
                .thenReturn(ResponseEntity.ok(chartResponse(70000.0, 69000.0)));
        when(naverNewsService.fetchNews(anyString(), anyInt()))
                .thenReturn(List.of(news("신고가 경신"), news("호실적 기대")));

        List<StockQuote> stocks = stockService.getTopStocks();

        assertThat(stocks).hasSize(5);  // top5
        StockQuote first = stocks.get(0);
        assertThat(first.isAvailable()).isTrue();
        assertThat(first.getPrice()).isEqualTo(70000.0);
        assertThat(first.getChangeAmount()).isEqualTo(1000.0);  // 70000 - 69000
        assertThat(first.isUp()).isTrue();
        assertThat(first.getChartData()).containsExactly(69000.0, 70000.0);
        assertThat(first.getHeadlines()).isNotEmpty();
    }

    @Test
    void getTopStocks_yahooFailure_marksUnavailable_butKeepsNews() {
        StockService stockService = new StockService(restTemplate, naverNewsService, sentimentService);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(YahooChartResponse.class)))
                .thenThrow(new RuntimeException("yahoo down"));
        when(naverNewsService.fetchNews(anyString(), anyInt()))
                .thenReturn(List.of(news("종목 관련 뉴스")));

        List<StockQuote> stocks = stockService.getTopStocks();

        assertThat(stocks).hasSize(5);
        assertThat(stocks.get(0).isAvailable()).isFalse();      // 시세 실패
        assertThat(stocks.get(0).getPrice()).isNull();
        assertThat(stocks.get(0).getHeadlines()).isNotEmpty();  // 뉴스는 유지
    }
}
