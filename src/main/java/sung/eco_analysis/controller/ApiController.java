package sung.eco_analysis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sung.eco_analysis.dto.AnalysisSummary;
import sung.eco_analysis.dto.NaverNewsItem;
import sung.eco_analysis.dto.RateChangeEvent;
import sung.eco_analysis.entity.RateHistory;
import sung.eco_analysis.service.ExchangeRateService;
import sung.eco_analysis.service.KeywordAnalysisService;
import sung.eco_analysis.service.NewsArchiveService;
import sung.eco_analysis.service.SnapshotService;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final ExchangeRateService exchangeRateService;
    private final NewsArchiveService newsArchiveService;
    private final KeywordAnalysisService keywordAnalysisService;
    private final SnapshotService snapshotService;

    @GetMapping("/rate/current")
    public ResponseEntity<Map<String, Object>> getCurrentRate() {
        Double rate = exchangeRateService.fetchCurrentUsdKrw();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currency", "USD/KRW");
        result.put("rate", rate);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/rate/history")
    public ResponseEntity<Map<String, Object>> getRateHistory(
            @RequestParam(defaultValue = "30") int days) {
        int safeDays = Math.min(Math.max(days, 1), 365);  // 1~365일로 제한
        List<RateHistory> history = exchangeRateService.getWeekdayDailyHistory(safeDays);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("labels", history.stream().map(h -> h.getRecordedAt().format(fmt)).collect(Collectors.toList()));
        result.put("data", history.stream().map(RateHistory::getRate).collect(Collectors.toList()));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/analysis")
    public ResponseEntity<Map<String, Object>> getAnalysis() {
        newsArchiveService.ingest();  // 최신 기사 적재(캐시 기반, 멱등) 후 아카이브에서 분석

        List<NaverNewsItem> news = newsArchiveService.getLatestNews(100);
        Map<String, Integer> keywords = keywordAnalysisService.analyzeKeywords(news);
        String summary = keywordAnalysisService.generateSummary(keywords, null, null);

        List<RateHistory> history = exchangeRateService.getRecentHistory(30);
        // 변동 원인 분석은 더 넓은 날짜 커버리지를 위해 아카이브 전체(최근 90일)를 사용
        List<NaverNewsItem> archivedNews = newsArchiveService.getRecentNews(90);
        List<RateChangeEvent> events = keywordAnalysisService.analyzeRateChangeEvents(history, archivedNews);
        int[] acc = snapshotService.getAccuracyStats();
        AnalysisSummary analysis = keywordAnalysisService.buildAnalysisSummary(keywords, acc[0], acc[1]);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("keywords", keywords);
        result.put("summary", summary);
        result.put("newsCount", news.size());
        result.put("pressureIndex", analysis.getPressureIndex());
        result.put("pressureLabel", analysis.getPressureLabel());
        result.put("accuracyAvailable", analysis.isAccuracyAvailable());
        result.put("accuracyPercent", analysis.isAccuracyAvailable() ? analysis.getAccuracyPercent() : null);
        result.put("changeEvents", events);
        return ResponseEntity.ok(result);
    }
}