package sung.eco_analysis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import sung.eco_analysis.dto.StockCorrelation;
import sung.eco_analysis.dto.StockQuote;
import sung.eco_analysis.service.StockFxCorrelationService;
import sung.eco_analysis.service.StockService;
import sung.eco_analysis.service.StockSnapshotService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class StockWebController {

    private final StockService stockService;
    private final StockSnapshotService stockSnapshotService;
    private final StockFxCorrelationService stockFxCorrelationService;

    @GetMapping("/stocks")
    public String stocks(Model model) {
        List<StockQuote> stocks = stockService.getTopStocks();
        boolean anyAvailable = stocks.stream().anyMatch(StockQuote::isAvailable);

        // 원/달러 환율과의 상관 분석 (수출주 동행/역행 인사이트)
        List<StockCorrelation> correlations = stockFxCorrelationService.analyze(stocks);
        boolean anyCorrelation = correlations.stream().anyMatch(StockCorrelation::isAvailable);
        model.addAttribute("correlations", correlations);
        model.addAttribute("anyCorrelation", anyCorrelation);

        // 종목별 감성 점수 추이 (최근 30일 스냅샷). 카드별 미니 차트로 표시.
        model.addAttribute("sentimentTrends", stockSnapshotService.recentSentimentTrends(30));

        // 감성 예측 적중률 (누적 스냅샷 기반). 평가된 건이 없으면 "산출 중"으로 표시.
        int[] acc = stockSnapshotService.getAccuracyStats();  // [matched, evaluated]
        boolean accuracyAvailable = acc[1] > 0;
        model.addAttribute("accuracyAvailable", accuracyAvailable);
        model.addAttribute("accuracyMatched", acc[0]);
        model.addAttribute("accuracyEvaluated", acc[1]);
        model.addAttribute("accuracyPercent", accuracyAvailable ? Math.round(acc[0] * 100.0 / acc[1]) : 0);

        model.addAttribute("stocks", stocks);
        model.addAttribute("anyAvailable", anyAvailable);
        model.addAttribute("lastUpdated",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        return "stocks";
    }
}
