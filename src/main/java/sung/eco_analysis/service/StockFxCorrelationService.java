package sung.eco_analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sung.eco_analysis.dto.StockCorrelation;
import sung.eco_analysis.dto.StockQuote;
import sung.eco_analysis.entity.RateHistory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 종목 일별 종가와 원/달러 환율의 상관관계를 분석한다.
 * <p>수출주는 통상 원화 약세(환율 상승) 국면에서 실적 기대가 커져 양(+)의 상관을 보이는 경향이 있다.
 * 종목 차트 라벨(MM/dd)과 환율 일별 히스토리를 같은 날짜 라벨로 매칭해 피어슨 상관계수를 구한다.
 */
@Service
@RequiredArgsConstructor
public class StockFxCorrelationService {

    private final ExchangeRateService exchangeRateService;

    // 상관계수 산출에 필요한 최소 짝지은 거래일 수. 너무 적으면 우연에 좌우돼 신뢰도가 낮다.
    private static final int MIN_SAMPLE = 5;
    private static final int WINDOW_DAYS = 90;
    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("MM/dd");

    public List<StockCorrelation> analyze(List<StockQuote> stocks) {
        // MM/dd -> 그 날 대표 환율
        Map<String, Double> fxByLabel = new LinkedHashMap<>();
        for (RateHistory h : exchangeRateService.getDailyRecentHistory(WINDOW_DAYS)) {
            fxByLabel.put(h.getRecordedAt().format(LABEL_FMT), h.getRate());
        }

        List<StockCorrelation> result = new ArrayList<>();
        for (StockQuote s : stocks) {
            result.add(correlateOne(s, fxByLabel));
        }
        // 상관 강도(절댓값) 높은 순. 표본 부족 종목은 뒤로.
        result.sort(Comparator
                .comparing((StockCorrelation c) -> c.isAvailable() ? 0 : 1)
                .thenComparing(c -> c.getCoefficient() == null ? 0.0 : -Math.abs(c.getCoefficient())));
        return result;
    }

    private StockCorrelation correlateOne(StockQuote s, Map<String, Double> fxByLabel) {
        List<String> labels = s.getChartLabels();
        List<Double> closes = s.getChartData();
        if (!s.isAvailable() || labels == null || closes == null || labels.isEmpty()) {
            return new StockCorrelation(s.getName(), s.getSymbol(), false, null, 0, "데이터 부족", false);
        }

        List<Double> xs = new ArrayList<>();  // 환율
        List<Double> ys = new ArrayList<>();  // 종가
        int n = Math.min(labels.size(), closes.size());
        for (int i = 0; i < n; i++) {
            Double fx = fxByLabel.get(labels.get(i));
            if (fx == null) continue;  // 환율 없는 날(휴장 불일치 등)은 제외
            xs.add(fx);
            ys.add(closes.get(i));
        }

        if (xs.size() < MIN_SAMPLE) {
            return new StockCorrelation(s.getName(), s.getSymbol(), false, null, xs.size(), "데이터 부족", false);
        }

        Double r = pearson(xs, ys);
        if (r == null) {  // 한쪽이 상수라 분산 0 → 상관 정의 불가
            return new StockCorrelation(s.getName(), s.getSymbol(), false, null, xs.size(), "데이터 부족", false);
        }
        return new StockCorrelation(s.getName(), s.getSymbol(), true, r, xs.size(), describe(r), r >= 0);
    }

    /** 두 동일 길이 시계열의 피어슨 상관계수. 분산이 0이면 null. */
    public static Double pearson(List<Double> xs, List<Double> ys) {
        int n = xs.size();
        double mx = xs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double my = ys.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double sxy = 0, sxx = 0, syy = 0;
        for (int i = 0; i < n; i++) {
            double dx = xs.get(i) - mx, dy = ys.get(i) - my;
            sxy += dx * dy;
            sxx += dx * dx;
            syy += dy * dy;
        }
        if (sxx == 0 || syy == 0) return null;
        return sxy / Math.sqrt(sxx * syy);
    }

    /** 상관계수를 한국어 강도/방향 라벨로 변환. */
    public static String describe(double r) {
        double a = Math.abs(r);
        String strength;
        if (a >= 0.7) strength = "강한";
        else if (a >= 0.4) strength = "뚜렷한";
        else if (a >= 0.2) strength = "약한";
        else return "상관 거의 없음";
        return strength + " " + (r >= 0 ? "동행(+)" : "역행(−)");
    }
}
