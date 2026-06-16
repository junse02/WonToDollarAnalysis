package sung.eco_analysis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import sung.eco_analysis.dto.FrankfurterResponse;
import sung.eco_analysis.dto.FrankfurterTimeSeriesResponse;
import sung.eco_analysis.entity.RateHistory;
import sung.eco_analysis.repository.RateHistoryRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    private final RestTemplate restTemplate;
    private final RateHistoryRepository rateHistoryRepository;

    private static final String BASE_URL = "https://api.frankfurter.app";
    private static final String CURRENCY = "USD/KRW";

    public Double fetchCurrentUsdKrw() {
        String url = BASE_URL + "/latest?from=USD&to=KRW";
        try {
            FrankfurterResponse response = restTemplate.getForObject(url, FrankfurterResponse.class);
            if (response != null && response.getRates() != null) {
                Double rate = response.getRates().get("KRW");
                log.info("현재 USD/KRW: {}", rate);
                return rate;
            }
        } catch (Exception e) {
            log.error("현재 환율 조회 실패: {}", e.getMessage());
        }
        return null;
    }

    public void fetchAndSaveCurrentRate() {
        Double rate = fetchCurrentUsdKrw();
        if (rate != null) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime hourStart = now.withMinute(0).withSecond(0).withNano(0);
            if (!rateHistoryRepository.existsByCurrencyAndRecordedAtBetween(
                    CURRENCY, hourStart, hourStart.plusHours(1))) {
                rateHistoryRepository.save(new RateHistory(CURRENCY, rate, now));
            }
        }
    }

    // 최근 7일 시계열을 한 번에 가져옴
    public void fetchAndSaveHistoricalRange() {
        LocalDate start = LocalDate.now().minusDays(7);
        LocalDate end = LocalDate.now().minusDays(1);
        String url = String.format("%s/%s..%s?from=USD&to=KRW", BASE_URL, start, end);
        try {
            FrankfurterTimeSeriesResponse response =
                    restTemplate.getForObject(url, FrankfurterTimeSeriesResponse.class);
            if (response == null || response.getRates() == null) return;

            for (Map.Entry<String, Map<String, Double>> entry : response.getRates().entrySet()) {
                LocalDate date = LocalDate.parse(entry.getKey());
                Double rate = entry.getValue().get("KRW");
                if (rate == null) continue;

                LocalDateTime recordedAt = LocalDateTime.of(date, LocalTime.NOON);
                if (!rateHistoryRepository.existsByCurrencyAndRecordedAtBetween(
                        CURRENCY, recordedAt.minusHours(12), recordedAt.plusHours(12))) {
                    rateHistoryRepository.save(new RateHistory(CURRENCY, rate, recordedAt));
                    log.info("과거 환율 저장 {}: {}", date, rate);
                }
            }
        } catch (Exception e) {
            log.warn("과거 환율 조회 실패: {}", e.getMessage());
        }
    }

    public List<RateHistory> getRecentHistory(int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        return rateHistoryRepository.findByCurrencyAndRecordedAtAfterOrderByRecordedAt(CURRENCY, from);
    }
}
