package sung.eco_analysis;

import org.junit.jupiter.api.Test;
import sung.eco_analysis.dto.NaverNewsItem;
import sung.eco_analysis.service.KeywordAnalysisService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordAnalysisServiceTest {

    private final KeywordAnalysisService service = new KeywordAnalysisService();

    // pubDate를 지정한 뉴스 아이템 생성 (정오 +0900)
    private NaverNewsItem newsOn(LocalDate date, String title) {
        NaverNewsItem item = new NaverNewsItem();
        // "EEE, dd MMM yyyy HH:mm:ss Z" 형식 (영어 로케일)
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        String[] dows = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        String dow = dows[date.getDayOfWeek().getValue() - 1];
        item.setPubDate(String.format("%s, %02d %s %d 12:00:00 +0900",
                dow, date.getDayOfMonth(), months[date.getMonthValue() - 1], date.getYear()));
        item.setTitle(title);
        item.setDescription("");
        return item;
    }

    // 날짜별 윈도우가 '당일 뉴스'로 구분돼, 강세일과 약세일의 지수 부호가 갈린다(변별력)
    @Test
    void historicalIndex_distinguishesDaysByOwnNews() {
        LocalDate hawkishDay = LocalDate.of(2026, 6, 18);
        LocalDate dovishDay = LocalDate.of(2026, 6, 19);

        List<NaverNewsItem> news = List.of(
                newsOn(hawkishDay, "연준 긴축 기조 강화, 기준금리 인상 시사"),
                newsOn(dovishDay, "연준 완화 피벗 기대, 금리인하 전망")
        );

        Map<LocalDate, Integer> idx = service.computeHistoricalPressureIndex(news);

        assertThat(idx.get(hawkishDay)).isGreaterThan(0);   // 달러 강세 우위
        assertThat(idx.get(dovishDay)).isLessThan(0);       // 달러 약세 우위
    }

    // 당일 뉴스(가중치 1.0)가 직전일 뉴스(가중치 0.6)를 이겨 지수 부호를 지배한다
    @Test
    void historicalIndex_currentDayDominatesPreviousDay() {
        LocalDate prevDay = LocalDate.of(2026, 6, 18);
        LocalDate targetDay = LocalDate.of(2026, 6, 19);

        // 직전일엔 강세 기사 1건, 당일엔 약세 기사 1건 -> 당일 가중치가 커 약세로 판정돼야 한다
        List<NaverNewsItem> news = List.of(
                newsOn(prevDay, "연준 긴축 기조"),
                newsOn(targetDay, "연준 완화 피벗")
        );

        Map<LocalDate, Integer> idx = service.computeHistoricalPressureIndex(news);

        assertThat(idx.get(targetDay)).isLessThan(0);  // 당일(약세)이 직전일(강세)을 압도
    }

    // 미래 기사는 지수에 반영되지 않는다(룩어헤드 방지)
    @Test
    void historicalIndex_ignoresFutureNews() {
        LocalDate targetDay = LocalDate.of(2026, 6, 18);
        LocalDate nextDay = LocalDate.of(2026, 6, 19);

        // 당일엔 강세 기사, 다음 날엔 강한 약세 기사 -> 당일 지수는 강세(>0)로 유지돼야 한다
        List<NaverNewsItem> news = List.of(
                newsOn(targetDay, "연준 긴축 기조"),
                newsOn(nextDay, "연준 완화 피벗, 금리인하")
        );

        Map<LocalDate, Integer> idx = service.computeHistoricalPressureIndex(news);

        assertThat(idx.get(targetDay)).isGreaterThan(0);  // 다음 날 약세 기사에 오염되지 않음
    }
}
