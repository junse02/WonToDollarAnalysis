package sung.eco_analysis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import sung.eco_analysis.config.ApiProperties;
import sung.eco_analysis.dto.ExchangeRateApiResponse;
import sung.eco_analysis.entity.RateHistory;
import sung.eco_analysis.repository.RateHistoryRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    private final RestTemplate restTemplate;
    private final ApiProperties apiProperties;
    private final RateHistoryRepository rateHistoryRepository;

    private static final String BASE_URL = "https://v6.exchangerate-api.com/v6";
    private static final String CURRENCY = "USD/KRW";

    public Double fetchCurrentUsdKrw() {
        String url = String.format("%s/%s/latest/USD",
                BASE_URL, apiProperties.getExchangeRate().getKey());
        try {
            // raw 응답 먼저 확인 (디버그용)
            String raw = restTemplate.getForObject(url, String.class);
            log.info("환율 API 응답: {}", raw != null ? raw.substring(0, Math.min(200, raw.length())) : "null");

            ExchangeRateApiResponse response = restTemplate.getForObject(url, ExchangeRateApiResponse.class);
            if (response != null && "success".equals(response.getResult()) && response.getConversionRates() != null) {
                return response.getConversionRates().get("KRW");
            }
            log.warn("환율 API 응답 이상 - result: {}", response != null ? response.getResult() : "null");
        } catch (Exception e) {
            log.error("환율 API 호출 실패 [{}]: {}", url.replaceAll(apiProperties.getExchangeRate().getKey(), "***"), e.getMessage());
        }
        return null;
    }

    public void fetchAndSaveCurrentRate() {
        Double rate = fetchCurrentUsdKrw();
        if (rate != null) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime hourStart = now.withMinute(0).withSecond(0).withNano(0);
            LocalDateTime hourEnd = hourStart.plusHours(1);

            // 같은 시간대 중복 저장 방지
            if (!rateHistoryRepository.existsByCurrencyAndRecordedAtBetween(CURRENCY, hourStart, hourEnd)) {
                rateHistoryRepository.save(new RateHistory(CURRENCY, rate, now));
                log.info("USD/KRW 저장: {}", rate);
            }
        }
    }

    public void fetchAndSaveHistoricalRate(LocalDate date) {
        String url = String.format("%s/%s/history/USD/%d/%d/%d",
                BASE_URL, apiProperties.getExchangeRate().getKey(),
                date.getYear(), date.getMonthValue(), date.getDayOfMonth());
        try {
            ExchangeRateApiResponse response = restTemplate.getForObject(url, ExchangeRateApiResponse.class);
            if (response != null && response.getConversionRates() != null) {
                Double rate = response.getConversionRates().get("KRW");
                if (rate != null) {
                    LocalDateTime recordedAt = date.atTime(12, 0);
                    if (!rateHistoryRepository.existsByCurrencyAndRecordedAtBetween(
                            CURRENCY, recordedAt.minusHours(12), recordedAt.plusHours(12))) {
                        rateHistoryRepository.save(new RateHistory(CURRENCY, rate, recordedAt));
                        log.info("과거 환율 저장 {}: {}", date.format(DateTimeFormatter.ISO_DATE), rate);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("과거 환율 조회 실패 ({}): {}", date, e.getMessage());
        }
    }

    public List<RateHistory> getRecentHistory(int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        return rateHistoryRepository.findByCurrencyAndRecordedAtAfterOrderByRecordedAt(CURRENCY, from);
    }
}