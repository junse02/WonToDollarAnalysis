package sung.eco_analysis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sung.eco_analysis.dto.NaverNewsItem;
import sung.eco_analysis.entity.RateHistory;
import sung.eco_analysis.service.ExchangeRateService;
import sung.eco_analysis.service.KeywordAnalysisService;
import sung.eco_analysis.service.NaverNewsService;

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
    private final NaverNewsService naverNewsService;
    private final KeywordAnalysisService keywordAnalysisService;

    @GetMapping("/rate/current")
    public ResponseEntity<Map<String, Object>> getCurrentRate() {
        Double rate = exchangeRateService.fetchCurrentUsdKrw();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currency", "USD/KRW");
        result.put("rate", rate);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/rate/history")
    public ResponseEntity<Map<String, Object>> getRateHistory() {
        List<RateHistory> history = exchangeRateService.getRecentHistory(7);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd HH:mm");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("labels", history.stream().map(h -> h.getRecordedAt().format(fmt)).collect(Collectors.toList()));
        result.put("data", history.stream().map(RateHistory::getRate).collect(Collectors.toList()));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/analysis")
    public ResponseEntity<Map<String, Object>> getAnalysis() {
        List<NaverNewsItem> news = naverNewsService.fetchExchangeRateNews(50);
        Map<String, Integer> keywords = keywordAnalysisService.analyzeKeywords(news);
        String summary = keywordAnalysisService.generateSummary(keywords, null, null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("keywords", keywords);
        result.put("summary", summary);
        result.put("newsCount", news.size());
        return ResponseEntity.ok(result);
    }
}