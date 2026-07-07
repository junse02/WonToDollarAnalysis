package sung.eco_analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sung.eco_analysis.entity.RateHistory;
import sung.eco_analysis.repository.RateHistoryRepository;
import sung.eco_analysis.service.ExchangeRateService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock RateHistoryRepository rateHistoryRepository;
    @InjectMocks ExchangeRateService service;

    private RateHistory rate(int month, int day, double v) {
        return new RateHistory("USD/KRW", v, LocalDateTime.of(2026, month, day, 12, 0));
    }

    // 2026-06-26(금)~07-01(수) 구간. 06-27(토)·06-28(일)은 ECB 미발표로 금요일 값이 반복됨.
    @Test
    void getWeekdayDailyHistory_excludesWeekends() {
        when(rateHistoryRepository.findByCurrencyAndRecordedAtAfterOrderByRecordedAt(eq("USD/KRW"), any()))
                .thenReturn(List.of(
                        rate(6, 26, 1536.47), // 금
                        rate(6, 27, 1536.47), // 토 (제외 대상)
                        rate(6, 28, 1536.47), // 일 (제외 대상)
                        rate(6, 29, 1543.75), // 월
                        rate(6, 30, 1550.89), // 화
                        rate(7, 1, 1558.09)   // 수
                ));

        List<RateHistory> result = service.getWeekdayDailyHistory(30);

        // 토·일 2개가 빠져 4개만 남고, 남은 날짜에 주말이 없어야 한다.
        assertThat(result).extracting(h -> h.getRecordedAt().toLocalDate())
                .containsExactly(
                        LocalDate.of(2026, 6, 26),
                        LocalDate.of(2026, 6, 29),
                        LocalDate.of(2026, 6, 30),
                        LocalDate.of(2026, 7, 1));
        assertThat(result).extracting(h -> h.getRecordedAt().getDayOfWeek())
                .noneMatch(d -> d == java.time.DayOfWeek.SATURDAY || d == java.time.DayOfWeek.SUNDAY);
    }
}
