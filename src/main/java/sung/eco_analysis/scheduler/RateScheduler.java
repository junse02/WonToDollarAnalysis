package sung.eco_analysis.scheduler;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sung.eco_analysis.service.ExchangeRateService;
import sung.eco_analysis.service.SnapshotService;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateScheduler {

    private final ExchangeRateService exchangeRateService;
    private final SnapshotService snapshotService;

    // 앱 시작 시 누락된 과거 데이터 백필 + 오늘 스냅샷 캡처/평가
    @PostConstruct
    public void initHistoricalData() {
        long before = exchangeRateService.getStoredCount();
        log.info("초기 환율 데이터 로드 시작 (기존 저장: {}건)", before);
        exchangeRateService.fetchAndSaveHistoricalRange();
        exchangeRateService.fetchAndSaveCurrentRate();
        long after = exchangeRateService.getStoredCount();
        log.info("초기 환율 데이터 로드 완료 (총 {}건, 신규 {}건)", after, after - before);

        // 스냅샷: 어제까지 미평가 건 평가 후 오늘 캡처
        snapshotService.evaluatePending();
        snapshotService.captureToday();
    }

    // 매 1시간마다 환율 갱신 + 스냅샷 갱신/평가
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public void scheduledRateFetch() {
        log.info("스케줄러: 환율 데이터 갱신");
        exchangeRateService.fetchAndSaveCurrentRate();
        snapshotService.evaluatePending();
        snapshotService.captureToday();
    }
}
