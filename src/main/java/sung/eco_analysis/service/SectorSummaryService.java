package sung.eco_analysis.service;

import org.springframework.stereotype.Service;
import sung.eco_analysis.dto.SectorSummary;
import sung.eco_analysis.dto.StockQuote;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 종목 리스트를 섹터(업종)별로 묶어 강세(평균 등락률)와 관심도(뉴스 검색 건수)를 집계한다.
 * <p>평균 등락률은 시세가 있는 종목만으로 계산하고, 관심도·감성은 모든 소속 종목을 합산한다.
 */
@Service
public class SectorSummaryService {

    /** 섹터별 요약을 강세(평균 등락률) 높은 순으로 반환. 시세 없는 섹터는 뒤로 민다. */
    public List<SectorSummary> summarize(List<StockQuote> stocks) {
        Map<String, Agg> bySector = new LinkedHashMap<>();  // 첫 등장 순서 보존
        for (StockQuote s : stocks) {
            if (s.getSector() == null) continue;
            Agg a = bySector.computeIfAbsent(s.getSector(), k -> new Agg());
            a.stockCount++;
            a.newsBuzz += s.getNewsBuzz();
            a.sentimentScore += s.getSentimentScore();
            if (s.isAvailable() && s.getChangePercent() != null) {
                a.changeSum += s.getChangePercent();
                a.pricedCount++;
            }
        }

        List<SectorSummary> result = new ArrayList<>();
        for (Map.Entry<String, Agg> e : bySector.entrySet()) {
            Agg a = e.getValue();
            Double avg = a.pricedCount > 0 ? a.changeSum / a.pricedCount : null;
            result.add(new SectorSummary(e.getKey(), a.stockCount, avg, a.newsBuzz, a.sentimentScore));
        }

        // 강세 순: 시세 있는 섹터를 앞에, 그 안에서 평균 등락률 내림차순
        result.sort(Comparator
                .comparing((SectorSummary s) -> s.isHasPrice() ? 0 : 1)
                .thenComparing(s -> s.getAvgChangePercent() == null ? 0.0 : -s.getAvgChangePercent()));
        return result;
    }

    private static final class Agg {
        int stockCount;
        int pricedCount;
        double changeSum;
        long newsBuzz;
        int sentimentScore;
    }
}
