package sung.eco_analysis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import sung.eco_analysis.dto.FrankfurterResponse;
import sung.eco_analysis.dto.FrankfurterTimeSeriesResponse;
import sung.eco_analysis.entity.RateHistory;
import sung.eco_analysis.repository.RateHistoryRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    private final RestTemplate restTemplate;
    private final RateHistoryRepository rateHistoryRepository;

    private static final String BASE_URL = "https://api.frankfurter.app";
    private static final String CURRENCY = "USD/KRW";

    @Cacheable(value = "currentRate", unless = "#result == null")
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

    // 환율의 실제 발표일(ECB 기준일). Frankfurter는 영업일 1회만 갱신되므로
    // 화면에 "조회 시각" 대신 이 날짜를 보여줘 갱신 여부를 정확히 알린다.
    @Cacheable(value = "currentRate", key = "'date'", unless = "#result == null")
    public LocalDate fetchCurrentRateDate() {
        String url = BASE_URL + "/latest?from=USD&to=KRW";
        try {
            FrankfurterResponse response = restTemplate.getForObject(url, FrankfurterResponse.class);
            if (response != null && response.getDate() != null) {
                return LocalDate.parse(response.getDate());
            }
        } catch (Exception e) {
            log.warn("환율 기준일 조회 실패: {}", e.getMessage());
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
        LocalDate start = LocalDate.now().minusDays(30);
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

    // 차트용: 날짜별 1개 대표값(그 날의 마지막 기록)으로 집계.
    // 최근일은 매시간 저장돼 점이 몰리므로 일자별로 묶어 균등하게 표시한다.
    public List<RateHistory> getDailyRecentHistory(int days) {
        Map<LocalDate, RateHistory> byDay = new LinkedHashMap<>();
        for (RateHistory h : getRecentHistory(days)) {
            byDay.put(h.getRecordedAt().toLocalDate(), h);  // 오름차순이므로 마지막 기록이 남음
        }
        return new ArrayList<>(byDay.values());
    }

    // 차트 표시용: 주말(토·일)은 ECB(Frankfurter)가 환율을 발표하지 않아 금요일 값이
    // 그대로 반복된다. 그래프가 며칠째 평평해 보이는 걸 막기 위해 영업일만 남긴다.
    // (상관분석·변동원인 분석은 원본 getDailyRecentHistory/getRecentHistory를 그대로 사용)
    public List<RateHistory> getWeekdayDailyHistory(int days) {
        return getDailyRecentHistory(days).stream()
                .filter(h -> {
                    DayOfWeek dow = h.getRecordedAt().getDayOfWeek();
                    return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
                })
                .collect(Collectors.toList());
    }

    public long getStoredCount() {
        return rateHistoryRepository.count();
    }

    // 가장 최근 저장된 환율 (실시간 조회 실패 시 폴백)
    public RateHistory getLatestStoredRate() {
        return rateHistoryRepository.findFirstByCurrencyOrderByRecordedAtDesc(CURRENCY).orElse(null);
    }
}
