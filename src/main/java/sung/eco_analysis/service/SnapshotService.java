package sung.eco_analysis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sung.eco_analysis.dto.NaverNewsItem;
import sung.eco_analysis.entity.DailySnapshot;
import sung.eco_analysis.entity.RateHistory;
import sung.eco_analysis.repository.DailySnapshotRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SnapshotService {

    private final DailySnapshotRepository snapshotRepository;
    private final ExchangeRateService exchangeRateService;
    private final NaverNewsService naverNewsService;
    private final KeywordAnalysisService keywordAnalysisService;

    // 오늘 분석 스냅샷 캡처 (날짜당 1건, 이미 있으면 최신값으로 갱신)
    public void captureToday() {
        Double rate = exchangeRateService.fetchCurrentUsdKrw();
        if (rate == null) {
            log.warn("환율 조회 실패로 스냅샷 캡처 건너뜀");
            return;
        }

        List<NaverNewsItem> news = naverNewsService.fetchExchangeRateNews(100);
        Map<String, Integer> keywords = keywordAnalysisService.analyzeKeywords(news);
        int index = keywordAnalysisService.computePressureIndex(keywords);
        Boolean predictedUp = keywordAnalysisService.predictedDirection(index);

        LocalDate today = LocalDate.now();
        DailySnapshot snap = snapshotRepository.findBySnapshotDate(today)
                .orElseGet(() -> new DailySnapshot(today, index, predictedUp, rate));
        snap.setPressureIndex(index);
        snap.setPredictedUp(predictedUp);
        snap.setRate(rate);
        snapshotRepository.save(snap);
        log.info("스냅샷 캡처 {}: 압력지수={} 예측={}", today, index,
                predictedUp == null ? "중립" : (predictedUp ? "강세" : "약세"));
    }

    // 미평가 스냅샷 중 이후 날짜 환율이 확보된 건을 평가
    public void evaluatePending() {
        List<DailySnapshot> pending = snapshotRepository.findByEvaluatedFalse();
        if (pending.isEmpty()) return;

        // 날짜 -> 환율 (일별 마지막 기록), 오름차순 정렬
        Map<LocalDate, Double> rateByDate = new TreeMap<>();
        for (RateHistory h : exchangeRateService.getRecentHistory(90)) {
            rateByDate.put(h.getRecordedAt().toLocalDate(), h.getRate());
        }

        int evaluatedNow = 0;
        for (DailySnapshot snap : pending) {
            // 스냅샷 날짜 이후 첫 환율 (TreeMap 오름차순 보장)
            Double laterRate = null;
            for (Map.Entry<LocalDate, Double> e : rateByDate.entrySet()) {
                if (e.getKey().isAfter(snap.getSnapshotDate())) {
                    laterRate = e.getValue();
                    break;
                }
            }
            if (laterRate == null) continue;  // 아직 다음 날 환율 없음

            boolean actualUp = laterRate > snap.getRate();
            snap.setActualUp(actualUp);
            snap.setEvaluated(true);
            // 중립(predictedUp=null)이면 matched는 null로 두어 적중률 집계에서 제외
            if (snap.getPredictedUp() != null) {
                snap.setMatched(snap.getPredictedUp() == actualUp);
            }
            snapshotRepository.save(snap);
            evaluatedNow++;
        }
        if (evaluatedNow > 0) log.info("스냅샷 사후 평가 완료: {}건", evaluatedNow);
    }

    // [matched, evaluated] 반환 (중립 제외, 방향 예측이 있었던 건만)
    public int[] getAccuracyStats() {
        List<DailySnapshot> evaluated = snapshotRepository.findByEvaluatedTrueAndMatchedIsNotNull();
        int total = evaluated.size();
        int matched = (int) evaluated.stream().filter(DailySnapshot::getMatched).count();
        return new int[]{matched, total};
    }
}
