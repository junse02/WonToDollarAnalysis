package sung.eco_analysis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import sung.eco_analysis.dto.AnalysisSummary;
import sung.eco_analysis.dto.NaverNewsItem;
import sung.eco_analysis.dto.RateChangeEvent;
import sung.eco_analysis.entity.RateHistory;
import sung.eco_analysis.service.ExchangeRateService;
import sung.eco_analysis.service.KeywordAnalysisService;
import sung.eco_analysis.service.NaverNewsService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class WebController {

    private final ExchangeRateService exchangeRateService;
    private final NaverNewsService naverNewsService;
    private final KeywordAnalysisService keywordAnalysisService;

    @GetMapping("/")
    public String index(Model model) {
        // 현재 환율
        Double currentRate = exchangeRateService.fetchCurrentUsdKrw();

        // 뉴스 조회 및 키워드 분석 (100건 - 날짜별 매핑용)
        List<NaverNewsItem> allNews = naverNewsService.fetchExchangeRateNews(100);
        Map<String, Integer> keywords = keywordAnalysisService.analyzeKeywords(allNews);
        String topKeyword = keywordAnalysisService.getTopKeyword(keywords);

        // 환율 히스토리 (최근 7일)
        List<RateHistory> history = exchangeRateService.getRecentHistory(30);

        // 차트 데이터
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd HH:mm");
        List<String> chartLabels = history.stream()
                .map(h -> h.getRecordedAt().format(fmt))
                .collect(Collectors.toList());
        List<Double> chartData = history.stream()
                .map(RateHistory::getRate)
                .collect(Collectors.toList());

        // 키워드 차트 데이터 (상위 7개)
        List<String> kwLabels = keywords.keySet().stream().limit(7).collect(Collectors.toList());
        List<Integer> kwValues = keywords.values().stream().limit(7).collect(Collectors.toList());

        // 분석 요약
        String summary = keywordAnalysisService.generateSummary(keywords, currentRate, history);

        // 날짜별 환율 변동 원인 분석
        List<RateChangeEvent> changeEvents = keywordAnalysisService.analyzeRateChangeEvents(history, allNews);

        // 압력 지수 + 적중률 종합
        AnalysisSummary analysisSummary = keywordAnalysisService.buildAnalysisSummary(keywords, changeEvents);

        model.addAttribute("currentRate", currentRate);
        model.addAttribute("newsCount", allNews.size());
        model.addAttribute("topKeyword", topKeyword);
        model.addAttribute("summary", summary);
        model.addAttribute("news", allNews.subList(0, Math.min(15, allNews.size())));
        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartData", chartData);
        model.addAttribute("kwLabels", kwLabels);
        model.addAttribute("kwValues", kwValues);
        model.addAttribute("changeEvents", changeEvents);
        model.addAttribute("analysis", analysisSummary);
        model.addAttribute("lastUpdated", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        return "index";
    }
}