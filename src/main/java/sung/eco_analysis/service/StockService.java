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

    // 국내 종목 (Yahoo 심볼, 코스피 .KS / 코스닥 .KQ). 3번째 인자는 테마/섹터 —
    // 대형주뿐 아니라 원자력·방산, 2차전지 소재 같은 인기 테마 대표주를 함께 담아
    // 상단 요약에서 "어느 테마가 강세·주목받는지"를 보여준다. 순위는 변동되므로 필요 시 갱신.
    private static final List<StockRef> TOP_STOCKS = List.of(
            // 반도체 (메모리·HBM 장비)
            new StockRef("삼성전자", "005930.KS", "반도체"),
            new StockRef("SK하이닉스", "000660.KS", "반도체"),
            new StockRef("한미반도체", "042700.KS", "반도체"),
            // 2차전지 (셀·양극재)
            new StockRef("LG에너지솔루션", "373220.KS", "2차전지"),
            new StockRef("에코프로비엠", "247540.KQ", "2차전지"),
            // 바이오·제약
            new StockRef("삼성바이오로직스", "207940.KS", "바이오·제약"),
            new StockRef("셀트리온", "068270.KS", "바이오·제약"),
            new StockRef("알테오젠", "196170.KQ", "바이오·제약"),
            // 자동차
            new StockRef("현대차", "005380.KS", "자동차"),
            new StockRef("기아", "000270.KS", "자동차"),
            // 방산·원자력
            new StockRef("한화에어로스페이스", "012450.KS", "방산·원자력"),
            new StockRef("두산에너빌리티", "034020.KS", "방산·원자력"),
            // 인터넷·플랫폼
            new StockRef("네이버", "035420.KS", "인터넷·플랫폼"),
            new StockRef("카카오", "035720.KS", "인터넷·플랫폼"),
            // 금융
            new StockRef("KB금융", "105560.KS", "금융"),
            new StockRef("신한지주", "055550.KS", "금융")
    );

    // 미국 종목 (Yahoo는 미국 티커를 접미사 없이 사용). 종목명은 네이버 한국어 뉴스 검색어로도 쓰인다.
    // 4번째 인자는 테마/섹터 — 대형주뿐 아니라 양자컴퓨팅·AI반도체 같은 인기 테마 대표주를 함께 담아
    // 상단 요약에서 "어느 테마가 강세·주목받는지"를 보여준다.
    private static final List<StockRef> TOP_US_STOCKS = List.of(
            // 빅테크·플랫폼
            new StockRef("애플", "AAPL", "애플 주가", "빅테크·플랫폼"),
            new StockRef("마이크로소프트", "MSFT", "마이크로소프트 주가", "빅테크·플랫폼"),
            new StockRef("구글", "GOOGL", "구글 알파벳 주가", "빅테크·플랫폼"),
            new StockRef("메타", "META", "메타 주가", "빅테크·플랫폼"),
            new StockRef("아마존", "AMZN", "아마존 주가", "빅테크·플랫폼"),
            // AI·반도체
            new StockRef("엔비디아", "NVDA", "엔비디아 주가", "AI·반도체"),
            new StockRef("브로드컴", "AVGO", "브로드컴 주가", "AI·반도체"),
            new StockRef("AMD", "AMD", "AMD 주가", "AI·반도체"),
            // 양자컴퓨팅
            new StockRef("아이온큐", "IONQ", "아이온큐 주가", "양자컴퓨팅"),
            new StockRef("리게티", "RGTI", "리게티 양자 주가", "양자컴퓨팅"),
            new StockRef("디웨이브", "QBTS", "디웨이브 양자 주가", "양자컴퓨팅"),
            // 전기차·자율주행
            new StockRef("테슬라", "TSLA", "테슬라 주가", "전기차·자율주행"),
            // 비만·헬스케어
            new StockRef("일라이릴리", "LLY", "일라이릴리 주가", "비만·헬스케어"),
            // 금융
            new StockRef("JP모건", "JPM", "JP모건 주가", "금융")
    );

    public List<StockQuote> getTopStocks() {
        return buildQuotes(TOP_STOCKS);
    }

    public List<StockQuote> getTopUsStocks() {
        return buildQuotes(TOP_US_STOCKS);
    }

    private List<StockQuote> buildQuotes(List<StockRef> refs) {
        List<StockQuote> result = new ArrayList<>();
        for (StockRef ref : refs) {
            result.add(buildQuote(ref));
        }
        return result;
    }

    private StockQuote buildQuote(StockRef ref) {
        YahooChartResponse.Result chart = fetchChart(ref.symbol());

        // 뉴스 + 감성 (국내: "종목명 주가"·최신순 / 미국: "종목명 주가"·관련도순으로 종목 집중 기사 우선)
        NaverNewsService.NewsSearch search = ref.relevanceSort()
                ? naverNewsService.fetchNewsByRelevance(ref.newsQuery(), NEWS_PER_STOCK)
                : naverNewsService.fetchNews(ref.newsQuery(), NEWS_PER_STOCK);
        List<NaverNewsItem> news = search.items();
        int newsBuzz = search.total();  // 섹터 관심도 지표 (검색어 전체 매칭 건수)
        StockSentimentService.Result sentiment = stockSentimentService.analyze(news);
        List<StockQuote.NewsHeadline> headlines = news.stream()
                .limit(HEADLINES_SHOWN)
                .map(n -> new StockQuote.NewsHeadline(
                        n.getCleanTitle(), n.getLink(),
                        stockSentimentService.classifyItem(n).getLabel()))
                .toList();

        if (chart == null || chart.getMeta() == null || chart.getMeta().getRegularMarketPrice() == null) {
            log.warn("종목 시세 조회 실패: {} ({})", ref.name(), ref.symbol());
            return new StockQuote(ref.name(), ref.symbol(), ref.sector(), null, null, null,
                    List.of(), List.of(), sentiment.label(), sentiment.score(), headlines, newsBuzz);
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

        return new StockQuote(ref.name(), ref.symbol(), ref.sector(), price, changeAmount, changePercent,
                labels, closes, sentiment.label(), sentiment.score(), headlines, newsBuzz);
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

    // newsQuery: 네이버 뉴스 검색어. 국내·미국 모두 "종목명 주가"로 한정해 종목과 무관한
    // 기사(포털·메신저 등 동명 노이즈)를 걸러 관심도(전체 검색 건수) 정확도를 높인다.
    // 정렬은 국내=최신순(date), 미국=관련도순(sim, relevanceSort=true).
    // sector: 페이지 상단 섹터 요약용 업종명.
    private record StockRef(String name, String symbol, String newsQuery, boolean relevanceSort, String sector) {
        // 국내: "종목명 주가" 검색 + 최신순
        StockRef(String name, String symbol, String sector) { this(name, symbol, name + " 주가", false, sector); }
        // 미국: 한정 검색어 + 관련도순
        StockRef(String name, String symbol, String newsQuery, String sector) {
            this(name, symbol, newsQuery, true, sector);
        }
    }
}
