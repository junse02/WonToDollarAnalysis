package sung.eco_analysis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import sung.eco_analysis.dto.NaverNewsItem;
import sung.eco_analysis.dto.StockQuote;
import sung.eco_analysis.dto.YahooChartResponse;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 국내 시가총액 상위 종목의 시세(Yahoo Finance, 무인증)와 뉴스 감성을 묶어 제공한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final RestTemplate restTemplate;
    private final NaverNewsService naverNewsService;
    private final StockSentimentService stockSentimentService;

    // 3개월(약 60거래일) 일별 시세 → 차트에서 30/90일 전환 가능
    private static final String YAHOO_CHART = "https://query1.finance.yahoo.com/v8/finance/chart/%s?range=3mo&interval=1d";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("MM/dd").withZone(KST);
    private static final int NEWS_PER_STOCK = 20;
    private static final int HEADLINES_SHOWN = 3;

    // 분석 대상 종목 (시가총액 상위권, Yahoo 심볼). 순위는 변동되므로 필요 시 갱신.
    private static final List<StockRef> TOP_STOCKS = List.of(
            new StockRef("삼성전자", "005930.KS"),
            new StockRef("SK하이닉스", "000660.KS"),
            new StockRef("LG에너지솔루션", "373220.KS"),
            new StockRef("삼성바이오로직스", "207940.KS"),
            new StockRef("현대차", "005380.KS"),
            new StockRef("기아", "000270.KS"),
            new StockRef("셀트리온", "068270.KS"),
            new StockRef("KB금융", "105560.KS"),
            new StockRef("네이버", "035420.KS"),
            new StockRef("신한지주", "055550.KS")
    );

    public List<StockQuote> getTopStocks() {
        List<StockQuote> result = new ArrayList<>();
        for (StockRef ref : TOP_STOCKS) {
            result.add(buildQuote(ref));
        }
        return result;
    }

    private StockQuote buildQuote(StockRef ref) {
        YahooChartResponse.Result chart = fetchChart(ref.symbol());

        // 뉴스 + 감성 (종목명으로 검색)
        List<NaverNewsItem> news = naverNewsService.fetchNews(ref.name(), NEWS_PER_STOCK);
        StockSentimentService.Result sentiment = stockSentimentService.analyze(news);
        List<StockQuote.NewsHeadline> headlines = news.stream()
                .limit(HEADLINES_SHOWN)
                .map(n -> new StockQuote.NewsHeadline(
                        n.getCleanTitle(), n.getLink(),
                        stockSentimentService.classifyItem(n).getLabel()))
                .toList();

        if (chart == null || chart.getMeta() == null || chart.getMeta().getRegularMarketPrice() == null) {
            log.warn("종목 시세 조회 실패: {} ({})", ref.name(), ref.symbol());
            return new StockQuote(ref.name(), ref.symbol(), null, null, null,
                    List.of(), List.of(), sentiment.label(), sentiment.score(), headlines);
        }

        List<String> labels = new ArrayList<>();
        List<Double> closes = new ArrayList<>();
        extractSeries(chart, labels, closes);

        double price = chart.getMeta().getRegularMarketPrice();
        // '전일 대비'는 시계열의 마지막 직전 종가 기준 (meta.chartPreviousClose는 1개월 전 종가라 부적합).
        Double prevClose = (closes.size() >= 2) ? closes.get(closes.size() - 2)
                : chart.getMeta().getChartPreviousClose();
        Double changeAmount = (prevClose != null) ? price - prevClose : null;
        Double changePercent = (prevClose != null && prevClose != 0) ? (changeAmount / prevClose) * 100 : null;

        return new StockQuote(ref.name(), ref.symbol(), price, changeAmount, changePercent,
                labels, closes, sentiment.label(), sentiment.score(), headlines);
    }

    // 휴장일(null 종가)은 건너뛰고 라벨·종가 시계열을 구성
    private void extractSeries(YahooChartResponse.Result chart, List<String> labels, List<Double> closes) {
        List<Long> ts = chart.getTimestamp();
        if (ts == null || chart.getIndicators() == null
                || chart.getIndicators().getQuote() == null
                || chart.getIndicators().getQuote().isEmpty()) {
            return;
        }
        List<Double> closeSeries = chart.getIndicators().getQuote().get(0).getClose();
        if (closeSeries == null) return;
        int n = Math.min(ts.size(), closeSeries.size());
        for (int i = 0; i < n; i++) {
            Double c = closeSeries.get(i);
            if (c == null) continue;
            labels.add(LABEL_FMT.format(Instant.ofEpochSecond(ts.get(i))));
            closes.add(c);
        }
    }

    @Cacheable(value = "stocks", key = "#symbol", unless = "#result == null")
    public YahooChartResponse.Result fetchChart(String symbol) {
        String url = String.format(YAHOO_CHART, symbol);
        try {
            // Yahoo는 User-Agent 없는 요청을 차단할 수 있어 명시
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (compatible; EcoAnalysis/1.0)");
            YahooChartResponse resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), YahooChartResponse.class).getBody();
            if (resp != null && resp.getChart() != null
                    && resp.getChart().getResult() != null
                    && !resp.getChart().getResult().isEmpty()) {
                return resp.getChart().getResult().get(0);
            }
        } catch (Exception e) {
            log.warn("Yahoo 시세 조회 실패 {}: {}", symbol, e.getMessage());
        }
        return null;
    }

    private record StockRef(String name, String symbol) {}
}
