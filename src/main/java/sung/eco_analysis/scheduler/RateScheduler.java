package sung.eco_analysis.scheduler;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sung.eco_analysis.service.ExchangeRateService;


@Component
@RequiredArgsConstructor
@Slf4j
public class RateScheduler {

    private final ExchangeRateService exchangeRateService;

    // 앱 시작 시 누락된 과거 데이터 백필 (이미 저장된 날짜는 건너뜀)
    @PostConstruct
    public void initHistoricalData() {
        long before = exchangeRateService.getStoredCount();
        log.info("초기 환율 데이터 로드 시작 (기존 저장: {}건)", before);
        exchangeRateService.fetchAndSaveHistoricalRange();
        exchangeRateService.fetchAndSaveCurrentRate();
        long after = exchangeRateService.getStoredCount();
        log.info("초기 환율 데이터 로드 완료 (총 {}건, 신규 {}건)", after, after - before);
    }

    // 매 1시간마다 현재 환율 저장
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public void scheduledRateFetch() {
        log.info("스케줄러: 환율 데이터 갱신");
        exchangeRateService.fetchAndSaveCurrentRate();
    }
}