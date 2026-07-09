package sung.eco_analysis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import sung.eco_analysis.dto.SectorSummary;
import sung.eco_analysis.dto.StockCorrelation;
import sung.eco_analysis.dto.StockQuote;
import sung.eco_analysis.service.SectorSummaryService;
import sung.eco_analysis.service.StockFxCorrelationService;
import sung.eco_analysis.service.StockService;
import sung.eco_analysis.service.StockSnapshotService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class StockWebController {

    private final StockService stockService;
    private final StockSnapshotService stockSnapshotService;
    private final StockFxCorrelationService stockFxCorrelationService;
    private final SectorSummaryService sectorSummaryService;

    @GetMapping("/stocks")
    public String stocks(Model model) {
        // 국내: 원화 표시 + 원/달러 환율 상관 분석 포함
        return buildPage(model, stockService.getTopStocks(), "KRW",
                "국내 상위 종목 분석", true, "/stocks/us", "미국 종목");
    }

    @GetMapping("/stocks/us")
    public String usStocks(Model model) {
        // 미국: 달러 표시 + 환율 상관 분석 제외(달러 표시 종목이라 의미가 다름)
        return buildPage(model, stockService.getTopUsStocks(), "USD",
                "미국 상위 종목 분석", false, "/stocks", "국내 종목");
    }

    private String buildPage(Model model, List<StockQuote> stocks, String currency,
                             String pageTitle, boolean withCorrelation,
                             String otherMarketUrl, String otherMarketLabel) {
        boolean anyAvailable = stocks.stream().anyMatch(StockQuote::isAvailable);

        // 감성 예측 적중률 (이 페이지 종목들만 집계)
        Set<String> symbols = stocks.stream().map(StockQuote::getSymbol).collect(Collectors.toSet());
        int[] acc = stockSnapshotService.getAccuracyStats(symbols);  // [matched, evaluated]
        boolean accuracyAvailable = acc[1] > 0;
        model.addAttribute("accuracyAvailable", accuracyAvailable);
        model.addAttribute("accuracyMatched", acc[0]);
        model.addAttribute("accuracyEvaluated", acc[1]);
        model.addAttribute("accuracyPercent", accuracyAvailable ? Math.round(acc[0] * 100.0 / acc[1]) : 0);

        // 원/달러 환율과의 상관 분석 (국내 페이지만)
        if (withCorrelation) {
            List<StockCorrelation> correlations = stockFxCorrelationService.analyze(stocks);
            model.addAttribute("correlations", correlations);
            model.addAttribute("anyCorrelation", correlations.stream().anyMatch(StockCorrelation::isAvailable));
        } else {
            model.addAttribute("correlations", List.of());
            model.addAttribute("anyCorrelation", false);
        }

        // 섹터별 강세·관심도 요약 (상단 카드)
        List<SectorSummary> sectors = sectorSummaryService.summarize(stocks);  // 강세순 정렬
        SectorSummary topStrong = sectors.stream()
                .filter(SectorSummary::isHasPrice).findFirst().orElse(null);  // 강세 1위
        SectorSummary topPopular = sectors.stream()
                .max(Comparator.comparingLong(SectorSummary::getNewsBuzz)).orElse(null);  // 관심 1위
        long maxBuzz = sectors.stream().mapToLong(SectorSummary::getNewsBuzz).max().orElse(0);
        model.addAttribute("sectors", sectors);
        model.addAttribute("sectorSummaryAvailable", !sectors.isEmpty());
        model.addAttribute("topStrongSector", topStrong);
        model.addAttribute("topPopularSector", (maxBuzz > 0) ? topPopular : null);
        model.addAttribute("maxBuzz", maxBuzz);

        // 종목별 감성 점수 추이 (최근 30일 스냅샷). 템플릿이 심볼로 매칭하므로 전체 전달.
        model.addAttribute("sentimentTrends", stockSnapshotService.recentSentimentTrends(30));

        model.addAttribute("stocks", stocks);
        model.addAttribute("anyAvailable", anyAvailable);
        model.addAttribute("currency", currency);
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("otherMarketUrl", otherMarketUrl);
        model.addAttribute("otherMarketLabel", otherMarketLabel);
        model.addAttribute("lastUpdated",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        return "stocks";
    }
}
