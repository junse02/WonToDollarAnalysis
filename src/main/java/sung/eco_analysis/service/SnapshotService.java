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

    // 의미 있는 변동으로 인정할 최소 환율 변동폭(±%). 이보다 작으면 '보합'으로 보고 채점하지 않는다.
    // 보합인 날(주말 동결 환율 등)은 건너뛰고, 시장이 실제로 움직인 다음 환율로 채점한다.
    // 끝까지 보합이면 강제로 실패 처리하지 않고 의미 있는 변동이 나올 때까지 평가를 보류한다.
    private static final double FLAT_THRESHOLD_PERCENT = 0.1;

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

    // 부트스트랩: 백필된 과거 환율 + 최근 뉴스 윈도우로 과거 날짜 스냅샷을 소급 생성한다.
    // 라이브 스냅샷(이미 존재하는 날짜)은 보존하며, 생성 후 즉시 사후 평가까지 수행한다.
    // 뉴스 API가 최근 기사만 제공하므로 소급 범위는 뉴스 윈도우(보통 최근 수일)로 제한된다.
    public void bootstrapFromHistory() {
        List<NaverNewsItem> news = naverNewsService.fetchExchangeRateNews(100);
        Map<LocalDate, Integer> indexByDate = keywordAnalysisService.computeHistoricalPressureIndex(news);
        if (indexByDate.isEmpty()) {
            log.info("부트스트랩 건너뜀: 뉴스 윈도우에서 날짜를 추출하지 못함");
            return;
        }

        // 날짜 -> 환율 (일별 마지막 기록)
        Map<LocalDate, Double> rateByDate = new TreeMap<>();
        for (RateHistory h : exchangeRateService.getRecentHistory(90)) {
            rateByDate.put(h.getRecordedAt().toLocalDate(), h.getRate());
        }

        LocalDate today = LocalDate.now();
        int created = 0;
        for (Map.Entry<LocalDate, Integer> e : indexByDate.entrySet()) {
            LocalDate date = e.getKey();
            if (!date.isBefore(today)) continue;                          // 오늘/미래는 captureToday 담당
            if (snapshotRepository.existsBySnapshotDate(date)) continue;  // 라이브 스냅샷 보존
            Double rateOnDate = rateByDate.get(date);
            if (rateOnDate == null) continue;                            // 그 날 환율 기준점 없음

            int index = e.getValue();
            Boolean predictedUp = keywordAnalysisService.predictedDirection(index);
            snapshotRepository.save(new DailySnapshot(date, index, predictedUp, rateOnDate));
            created++;
        }

        if (created > 0) {
            log.info("과거 스냅샷 부트스트랩: {}건 생성", created);
            evaluatePending();  // 다음 날 환율이 확보된 건은 즉시 평가
        } else {
            log.info("부트스트랩: 새로 생성할 과거 스냅샷 없음");
        }
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
            // 스냅샷 날짜 이후 '의미 있는 변동(±임계치 이상)'이 처음 나타난 환율을 찾는다.
            // 주말 동결(금요일 종가가 토·일에 그대로 기록)처럼 보합인 날은 건너뛰고,
            // 시장이 실제로 움직인 다음 영업일 환율로 채점한다. (TreeMap 오름차순 보장)
            Double movedRate = null;
            for (Map.Entry<LocalDate, Double> e : rateByDate.entrySet()) {
                if (!e.getKey().isAfter(snap.getSnapshotDate())) continue;
                double changePercent = (e.getValue() - snap.getRate()) / snap.getRate() * 100;
                if (Math.abs(changePercent) >= FLAT_THRESHOLD_PERCENT) {
                    movedRate = e.getValue();
                    break;
                }
            }
            if (movedRate == null) continue;  // 아직 의미 있는 변동 없음(보합·주말 동결) → 평가 보류

            boolean actualUp = movedRate > snap.getRate();
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
