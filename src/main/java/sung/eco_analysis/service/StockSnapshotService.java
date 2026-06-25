package sung.eco_analysis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sung.eco_analysis.dto.StockQuote;
import sung.eco_analysis.entity.StockSnapshot;
import sung.eco_analysis.repository.StockSnapshotRepository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 종목 뉴스 감성 예측의 적중률을 평가한다.
 * <p>환율 {@link SnapshotService}와 동일한 캡처→평가→적중률 패턴:
 * 매일 종목별 감성 점수·예측 방향·종가를 스냅샷으로 남기고,
 * 이후 거래일에 종가가 의미 있게 움직이면 예측이 맞았는지 채점한다.
 * <p>과거 종목별 뉴스 감성을 소급 복원하기 어려워 부트스트랩은 하지 않고,
 * 가동 이후부터 적중률을 누적한다(콜드 스타트).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockSnapshotService {

    private final StockSnapshotRepository snapshotRepository;

    // 의미 있는 변동으로 인정할 최소 종가 변동폭(±%). 이보다 작으면 '보합'으로 보고 채점을 보류한다.
    // 종목은 환율보다 변동성이 커 환율(0.1%)보다 높게 둔다.
    private static final double FLAT_THRESHOLD_PERCENT = 0.5;

    /** 감성 점수 → 예측 방향. 호재 우세(+2↑)=상승, 악재 우세(-2↓)=하락, 그 사이=중립(보류). */
    public static Boolean predictedDirection(int sentimentScore) {
        if (sentimentScore >= 2) return true;
        if (sentimentScore <= -2) return false;
        return null;
    }

    /** 오늘 종목 스냅샷 캡처 (종목·날짜당 1건, 이미 있으면 최신값으로 갱신). */
    public void captureToday(List<StockQuote> stocks) {
        LocalDate today = LocalDate.now();
        int saved = 0;
        for (StockQuote q : stocks) {
            if (!q.isAvailable()) continue;  // 시세 없으면 채점 기준점이 없어 건너뜀
            Boolean predictedUp = predictedDirection(q.getSentimentScore());
            StockSnapshot snap = snapshotRepository.findBySymbolAndSnapshotDate(q.getSymbol(), today)
                    .orElseGet(() -> new StockSnapshot(q.getSymbol(), q.getName(), today,
                            q.getSentimentScore(), predictedUp, q.getPrice()));
            snap.setName(q.getName());
            snap.setSentimentScore(q.getSentimentScore());
            snap.setPredictedUp(predictedUp);
            snap.setPrice(q.getPrice());
            snapshotRepository.save(snap);
            saved++;
        }
        if (saved > 0) log.info("종목 스냅샷 캡처 {}: {}건", today, saved);
    }

    /** 미평가 스냅샷 중, 이후 거래일 종가가 의미 있게 움직인 건을 채점한다. */
    public void evaluatePending() {
        List<StockSnapshot> pending = snapshotRepository.findByEvaluatedFalse();
        if (pending.isEmpty()) return;

        // 심볼 -> (날짜 -> 종가). 스냅샷 누적분이 곧 종목별 가격 시계열.
        Map<String, TreeMap<LocalDate, Double>> priceBySymbol = new HashMap<>();
        for (StockSnapshot s : snapshotRepository.findAll()) {
            priceBySymbol.computeIfAbsent(s.getSymbol(), k -> new TreeMap<>())
                    .put(s.getSnapshotDate(), s.getPrice());
        }

        int evaluatedNow = 0;
        for (StockSnapshot snap : pending) {
            TreeMap<LocalDate, Double> series = priceBySymbol.get(snap.getSymbol());
            if (series == null) continue;

            // 스냅샷 날짜 이후 '의미 있는 변동(±임계치 이상)'이 처음 나타난 종가를 찾는다.
            Double movedPrice = null;
            for (Map.Entry<LocalDate, Double> e : series.entrySet()) {
                if (!e.getKey().isAfter(snap.getSnapshotDate())) continue;
                double changePercent = (e.getValue() - snap.getPrice()) / snap.getPrice() * 100;
                if (Math.abs(changePercent) >= FLAT_THRESHOLD_PERCENT) {
                    movedPrice = e.getValue();
                    break;
                }
            }
            if (movedPrice == null) continue;  // 아직 의미 있는 변동 없음 → 평가 보류

            boolean actualUp = movedPrice > snap.getPrice();
            snap.setActualUp(actualUp);
            snap.setEvaluated(true);
            // 중립(predictedUp=null)이면 matched는 null로 두어 적중률 집계에서 제외
            if (snap.getPredictedUp() != null) {
                snap.setMatched(snap.getPredictedUp() == actualUp);
            }
            snapshotRepository.save(snap);
            evaluatedNow++;
        }
        if (evaluatedNow > 0) log.info("종목 스냅샷 사후 평가 완료: {}건", evaluatedNow);
    }

    /** [matched, evaluated] 반환 (중립 제외, 방향 예측이 있었던 건만). */
    public int[] getAccuracyStats() {
        List<StockSnapshot> evaluated = snapshotRepository.findByEvaluatedTrueAndMatchedIsNotNull();
        int total = evaluated.size();
        int matched = (int) evaluated.stream().filter(StockSnapshot::getMatched).count();
        return new int[]{matched, total};
    }
}
