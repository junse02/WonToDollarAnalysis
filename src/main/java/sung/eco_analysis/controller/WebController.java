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
import sung.eco_analysis.service.SnapshotService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class WebController {

    private final ExchangeRateService exchangeRateService;
    private final NaverNewsService naverNewsService;
    private final KeywordAnalysisService keywordAnalysisService;
    private final SnapshotService snapshotService;

    @GetMapping("/")
    public String index(Model model) {
        List<String> warnings = new ArrayList<>();

        // 현재 환율 - 실패 시 DB 마지막 저장값으로 폴백
        Double currentRate = exchangeRateService.fetchCurrentUsdKrw();
        boolean rateStale = false;
        String rateDate = null;  // 환율의 실제 발표일(영업일 1회 갱신) - 조회 시각과 구분
        if (currentRate == null) {
            RateHistory last = exchangeRateService.getLatestStoredRate();
            if (last != null) {
                currentRate = last.getRate();
                rateStale = true;
                rateDate = last.getRecordedAt().toLocalDate().format(DateTimeFormatter.ISO_DATE);
                warnings.add("환율 API 응답이 지연되어 마지막 저장값("
                        + last.getRecordedAt().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
                        + " 기준)을 표시합니다.");
            } else {
                warnings.add("환율 데이터를 일시적으로 불러오지 못했습니다.");
            }
        } else {
            LocalDate d = exchangeRateService.fetchCurrentRateDate();
            if (d != null) {
                rateDate = d.format(DateTimeFormatter.ISO_DATE);
            }
        }

        // 뉴스 조회 및 키워드 분석 (100건 - 날짜별 매핑용)
        List<NaverNewsItem> allNews = naverNewsService.fetchExchangeRateNews(100);
        if (allNews.isEmpty()) {
            warnings.add("네이버 뉴스를 일시적으로 불러오지 못했습니다. 잠시 후 새로고침해 주세요.");
        }
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

        // 압력 지수(현재 뉴스) + 적중률(누적 스냅샷)
        int[] acc = snapshotService.getAccuracyStats();  // [matched, evaluated]
        AnalysisSummary analysisSummary = keywordAnalysisService.buildAnalysisSummary(keywords, acc[0], acc[1]);

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
        model.addAttribute("warnings", warnings);
        model.addAttribute("rateStale", rateStale);
        model.addAttribute("rateDate", rateDate);
        model.addAttribute("lastUpdated", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        return "index";
    }
}