package sung.eco_analysis;

import org.junit.jupiter.api.Test;
import sung.eco_analysis.dto.NaverNewsItem;
import sung.eco_analysis.service.KeywordAnalysisService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    // LLM 분류 결과(categories)가 있으면 키워드 매칭을 무시하고 그 결과를 사용한다.
    // 부정문("긴축 우려 해소")은 키워드로는 오탐되지만, 분류 결과가 빈 집합이면 0으로 집계된다.
    @Test
    void analyze_usesClassifiedCategories_overKeywordMatch() {
        NaverNewsItem negated = new NaverNewsItem();
        negated.setTitle("연준 긴축 우려 해소, 금리인상 가능성 낮다");  // 키워드론 '긴축'+'금리인상' 매칭
        negated.setDescription("");
        negated.setCategories(Set.of());  // LLM: 해당 원인 없음

        Map<String, Integer> counts = service.analyzeKeywords(List.of(negated));

        assertThat(counts.get("연준 긴축·금리 인상")).isZero();
        assertThat(counts.get("연준 완화·금리 인하")).isZero();
    }

    // 분류 결과의 카테고리는 본문에 키워드가 없어도 집계된다(문맥 기반 분류).
    @Test
    void analyze_countsClassifiedCategory_withoutKeyword() {
        NaverNewsItem item = new NaverNewsItem();
        item.setTitle("물가 관련 직접 키워드 없는 제목");
        item.setDescription("");
        item.setCategories(Set.of("인플레이션·물가"));  // LLM이 문맥으로 분류

        Map<String, Integer> counts = service.analyzeKeywords(List.of(item));

        assertThat(counts.get("인플레이션·물가")).isEqualTo(1);
    }
}
