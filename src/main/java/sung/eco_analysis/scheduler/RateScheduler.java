package sung.eco_analysis.scheduler;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sung.eco_analysis.service.ExchangeRateService;
import sung.eco_analysis.service.NewsArchiveService;
import sung.eco_analysis.service.SnapshotService;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateScheduler {

    private final ExchangeRateService exchangeRateService;
    private final NewsArchiveService newsArchiveService;
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

        // 뉴스 아카이브에 최신 기사 적재 (DB에 누적 → 부트스트랩이 더 긴 히스토리 활용)
        newsArchiveService.ingest();

        // 스냅샷: 백필된 과거 환율+뉴스로 소급 생성 → 미평가 건 평가 → 오늘 캡처
        snapshotService.bootstrapFromHistory();
        snapshotService.evaluatePending();
        snapshotService.captureToday();
    }

    // 매 1시간마다 환율 갱신 + 스냅샷 갱신/평가
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public void scheduledRateFetch() {
        log.info("스케줄러: 환율 데이터 갱신");
        exchangeRateService.fetchAndSaveCurrentRate();
        newsArchiveService.ingest();
        snapshotService.evaluatePending();
        snapshotService.captureToday();
    }
}
