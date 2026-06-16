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

    // 앱 시작 시 최근 7일 과거 데이터 초기 로드
    @PostConstruct
    public void initHistoricalData() {
        log.info("초기 환율 데이터 로드 시작");
        exchangeRateService.fetchAndSaveHistoricalRange();
        exchangeRateService.fetchAndSaveCurrentRate();
        log.info("초기 환율 데이터 로드 완료");
    }

    // 매 1시간마다 현재 환율 저장
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public void scheduledRateFetch() {
        log.info("스케줄러: 환율 데이터 갱신");
        exchangeRateService.fetchAndSaveCurrentRate();
    }
}