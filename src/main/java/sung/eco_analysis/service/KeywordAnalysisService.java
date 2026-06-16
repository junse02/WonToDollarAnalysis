package sung.eco_analysis.service;

import org.springframework.stereotype.Service;
import sung.eco_analysis.dto.AnalysisSummary;
import sung.eco_analysis.dto.NaverNewsItem;
import sung.eco_analysis.dto.RateChangeEvent;
import sung.eco_analysis.entity.RateHistory;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KeywordAnalysisService {

    private static final Map<String, List<String>> KEYWORD_CATEGORIES = new LinkedHashMap<>();

    static {
        KEYWORD_CATEGORIES.put("연준 긴축·금리 인상", Arrays.asList(
                "금리인상", "기준금리 인상", "연준 긴축", "Fed 긴축", "FOMC 긴축", "금리 동결", "hawkish", "긴축"
        ));
        KEYWORD_CATEGORIES.put("연준 완화·금리 인하", Arrays.asList(
                "금리인하", "기준금리 인하", "연준 완화", "Fed 완화", "FOMC 완화", "dovish", "양적완화", "피벗"
        ));
        KEYWORD_CATEGORIES.put("인플레이션·물가", Arrays.asList(
                "인플레이션", "물가상승", "물가 급등", "CPI", "PCE", "인플레", "소비자물가"
        ));
        KEYWORD_CATEGORIES.put("무역·수출 호조", Arrays.asList(
                "수출 증가", "무역흑자", "경상수지 흑자", "수출 호조", "경상흑자"
        ));
        KEYWORD_CATEGORIES.put("무역·수입 압박", Arrays.asList(
                "무역적자", "수입 증가", "경상수지 적자", "수출 감소", "경상적자"
        ));
        KEYWORD_CATEGORIES.put("달러 강세", Arrays.asList(
                "달러 강세", "강달러", "달러 급등", "달러화 강세", "달러 상승"
        ));
        KEYWORD_CATEGORIES.put("달러 약세", Arrays.asList(
                "달러 약세", "약달러", "달러 하락", "달러화 약세", "달러 급락"
        ));
        KEYWORD_CATEGORIES.put("경기침체 우려", Arrays.asList(
                "경기침체", "침체 우려", "경기 둔화", "불황", "성장 둔화", "경기 위축"
        ));
        KEYWORD_CATEGORIES.put("지정학 리스크", Arrays.asList(
                "북한", "지정학", "전쟁", "분쟁", "긴장", "위기", "안보"
        ));
        KEYWORD_CATEGORIES.put("외국인 자금 유입", Arrays.asList(
                "외국인 매수", "외국인 순매수", "외자 유입", "자금 유입", "외국인 투자"
        ));
    }

    // 달러 강세 요인 카테고리
    private static final Set<String> RATE_UP_FACTORS = new HashSet<>(Arrays.asList(
            "연준 긴축·금리 인상", "달러 강세", "지정학 리스크", "경기침체 우려", "무역·수입 압박"
    ));
    // 달러 약세 요인 카테고리
    private static final Set<String> RATE_DOWN_FACTORS = new HashSet<>(Arrays.asList(
            "연준 완화·금리 인하", "달러 약세", "무역·수출 호조", "외국인 자금 유입"
    ));

    // 카테고리별 시장 영향 가중치 (금리 > 환율 직접 언급 > 무역/수급 > 기타)
    private static final Map<String, Double> FACTOR_WEIGHTS = new HashMap<>();
    static {
        FACTOR_WEIGHTS.put("연준 긴축·금리 인상", 3.0);
        FACTOR_WEIGHTS.put("연준 완화·금리 인하", 3.0);
        FACTOR_WEIGHTS.put("달러 강세", 2.0);
        FACTOR_WEIGHTS.put("달러 약세", 2.0);
        FACTOR_WEIGHTS.put("지정학 리스크", 2.0);
        FACTOR_WEIGHTS.put("무역·수출 호조", 2.0);
        FACTOR_WEIGHTS.put("무역·수입 압박", 1.5);
        FACTOR_WEIGHTS.put("경기침체 우려", 1.5);
        FACTOR_WEIGHTS.put("외국인 자금 유입", 1.5);
    }

    public Map<String, Integer> analyzeKeywords(List<NaverNewsItem> newsItems) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        KEYWORD_CATEGORIES.keySet().forEach(k -> counts.put(k, 0));

        for (NaverNewsItem item : newsItems) {
            String text = (item.getCleanTitle() + " " + item.getCleanDescription()).toLowerCase();
            for (Map.Entry<String, List<String>> entry : KEYWORD_CATEGORIES.entrySet()) {
                for (String keyword : entry.getValue()) {
                    if (text.contains(keyword.toLowerCase())) {
                        counts.merge(entry.getKey(), 1, Integer::sum);
                        break;
                    }
                }
            }
        }
        return sortByValueDesc(counts);
    }

    public String getTopKeyword(Map<String, Integer> keywords) {
        return keywords.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse("분석 중");
    }

    public String generateSummary(Map<String, Integer> keywords, Double currentRate, List<?> history) {
        int rateUp = RATE_UP_FACTORS.stream().mapToInt(k -> keywords.getOrDefault(k, 0)).sum();
        int rateDown = RATE_DOWN_FACTORS.stream().mapToInt(k -> keywords.getOrDefault(k, 0)).sum();

        String trend;
        if (rateUp > rateDown) trend = "달러 강세(원화 약세) 요인이 우세합니다.";
        else if (rateDown > rateUp) trend = "달러 약세(원화 강세) 요인이 우세합니다.";
        else trend = "방향성이 혼재되어 있습니다.";

        return String.format("최근 뉴스 분석 결과, '%s' 관련 기사가 가장 많습니다. %s",
                getTopKeyword(keywords), trend);
    }

    // ── 날짜별 환율 변동 원인 분석 ──────────────────────────────────────────

    public List<RateChangeEvent> analyzeRateChangeEvents(
            List<RateHistory> history, List<NaverNewsItem> allNews) {

        if (history.size() < 2) return Collections.emptyList();

        // 날짜별 대표 환율 (마지막 기록 사용)
        Map<LocalDate, Double> dailyRates = new LinkedHashMap<>();
        history.forEach(h -> dailyRates.put(h.getRecordedAt().toLocalDate(), h.getRate()));

        // 날짜별 뉴스 그룹핑
        Map<LocalDate, List<NaverNewsItem>> newsByDate = groupNewsByDate(allNews);

        List<LocalDate> dates = new ArrayList<>(dailyRates.keySet());

        List<RateChangeEvent> events = new ArrayList<>();
        for (int i = 1; i < dates.size(); i++) {
            LocalDate today = dates.get(i);
            LocalDate yesterday = dates.get(i - 1);
            double currentRate = dailyRates.get(today);
            double prevRate = dailyRates.get(yesterday);

            double changeAmount = currentRate - prevRate;
            double changePercent = (changeAmount / prevRate) * 100;

            // 0.2% 미만 변동은 제외 (노이즈)
            if (Math.abs(changePercent) < 0.2) continue;

            // 해당 날짜 ±1일 뉴스 수집
            List<NaverNewsItem> relatedNews = getNewsAroundDate(newsByDate, today);
            Map<String, Integer> eventKeywords = analyzeKeywords(relatedNews);
            String topKeyword = getTopKeyword(eventKeywords);

            List<String> newsTitles = relatedNews.stream()
                    .limit(3)
                    .map(NaverNewsItem::getCleanTitle)
                    .collect(Collectors.toList());

            String analysis = generateEventAnalysis(changePercent, eventKeywords, relatedNews.isEmpty());

            // 키워드가 예측한 방향과 실제 변동 방향 일치 여부
            Boolean matched = predictMatch(eventKeywords, changeAmount > 0);

            events.add(new RateChangeEvent(
                    today.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일")),
                    currentRate,
                    changeAmount,
                    changePercent,
                    changeAmount > 0,
                    newsTitles,
                    topKeyword,
                    analysis,
                    matched
            ));
        }

        // 절댓값 변동 큰 순 정렬 후 상위 10개
        events.sort((a, b) -> Double.compare(Math.abs(b.getChangePercent()), Math.abs(a.getChangePercent())));
        return events.stream().limit(10).collect(Collectors.toList());
    }

    // ── 압력 지수 + 적중률 종합 ─────────────────────────────────────────────

    // 달러 강세 압력 지수: 카테고리 가중 합산으로 -100~+100 산출
    public int computePressureIndex(Map<String, Integer> keywords) {
        double up = 0, down = 0;
        for (String cat : RATE_UP_FACTORS) {
            up += keywords.getOrDefault(cat, 0) * FACTOR_WEIGHTS.getOrDefault(cat, 1.0);
        }
        for (String cat : RATE_DOWN_FACTORS) {
            down += keywords.getOrDefault(cat, 0) * FACTOR_WEIGHTS.getOrDefault(cat, 1.0);
        }
        double total = up + down;
        return (total == 0) ? 0 : (int) Math.round((up - down) / total * 100);
    }

    // 압력 지수로 방향 예측: true=강세(상승), false=약세(하락), null=중립
    public Boolean predictedDirection(int index) {
        if (index > 10) return true;
        if (index < -10) return false;
        return null;
    }

    // 적중률(matched/evaluated)은 누적 스냅샷에서 계산해 주입받음
    public AnalysisSummary buildAnalysisSummary(
            Map<String, Integer> keywords, int matchedCount, int evaluatedCount) {

        int index = computePressureIndex(keywords);
        int gauge = (index + 100) / 2;  // -100~100 -> 0~100
        String label = pressureLabel(index);
        int accuracy = (evaluatedCount == 0) ? 0 : (int) Math.round((double) matchedCount / evaluatedCount * 100);

        String summaryText;
        if (evaluatedCount == 0) {
            summaryText = String.format(
                    "달러 강세 압력 지수 %+d (%s). 적중률은 일별 스냅샷이 누적되면 산출됩니다.",
                    index, label);
        } else {
            summaryText = String.format(
                    "달러 강세 압력 지수 %+d (%s). 누적 예측 %d일 중 %d일이 실제 환율 방향과 일치했습니다 (적중률 %d%%).",
                    index, label, evaluatedCount, matchedCount, accuracy);
        }

        return new AnalysisSummary(index, label, gauge, accuracy, matchedCount, evaluatedCount, summaryText);
    }

    private String pressureLabel(int index) {
        if (index >= 40) return "강한 달러 강세 압력 (원화 약세 심화)";
        if (index >= 10) return "달러 강세 우위 (원화 약세)";
        if (index > -10) return "중립 / 혼조";
        if (index > -40) return "달러 약세 우위 (원화 강세)";
        return "강한 달러 약세 압력 (원화 강세 심화)";
    }

    // 뉴스에서 가장 우세한 '방향성 있는' 카테고리로 변동 방향 예측 후 실제와 비교
    private Boolean predictMatch(Map<String, Integer> keywords, boolean actualUp) {
        String dirCat = keywords.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .filter(e -> RATE_UP_FACTORS.contains(e.getKey()) || RATE_DOWN_FACTORS.contains(e.getKey()))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(null);
        if (dirCat == null) return null;  // 예측 불가
        boolean predictedUp = RATE_UP_FACTORS.contains(dirCat);
        return predictedUp == actualUp;
    }

    private Map<LocalDate, List<NaverNewsItem>> groupNewsByDate(List<NaverNewsItem> news) {
        Map<LocalDate, List<NaverNewsItem>> map = new HashMap<>();
        for (NaverNewsItem item : news) {
            ZonedDateTime date = item.getParsedDate();
            if (date != null) {
                map.computeIfAbsent(date.toLocalDate(), k -> new ArrayList<>()).add(item);
            }
        }
        return map;
    }

    private List<NaverNewsItem> getNewsAroundDate(
            Map<LocalDate, List<NaverNewsItem>> newsByDate, LocalDate date) {
        List<NaverNewsItem> result = new ArrayList<>();
        for (int offset = -1; offset <= 1; offset++) {
            List<NaverNewsItem> dayNews = newsByDate.get(date.plusDays(offset));
            if (dayNews != null) result.addAll(dayNews);
        }
        return result;
    }

    private String generateEventAnalysis(double changePercent, Map<String, Integer> keywords, boolean noNews) {
        if (noNews) {
            return changePercent > 0
                    ? "관련 뉴스를 찾지 못했습니다. 글로벌 달러 강세 흐름이 영향을 준 것으로 보입니다."
                    : "관련 뉴스를 찾지 못했습니다. 글로벌 달러 약세 흐름이 영향을 준 것으로 보입니다.";
        }

        String topCategory = keywords.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(null);

        if (topCategory == null) {
            return changePercent > 0
                    ? "뚜렷한 단일 원인 없이 달러 강세 흐름이 나타났습니다."
                    : "뚜렷한 단일 원인 없이 달러 약세 흐름이 나타났습니다.";
        }

        boolean directionMatch = (changePercent > 0 && RATE_UP_FACTORS.contains(topCategory))
                || (changePercent < 0 && RATE_DOWN_FACTORS.contains(topCategory));

        String directionStr = changePercent > 0
                ? String.format("달러가 %.2f%% 상승(원화 약세)", changePercent)
                : String.format("달러가 %.2f%% 하락(원화 강세)", Math.abs(changePercent));

        if (directionMatch) {
            return String.format("%s했습니다. '%s' 관련 보도가 주요 원인으로 분석됩니다.", directionStr, topCategory);
        } else {
            return String.format("%s했습니다. '%s' 보도가 있었으나 다른 요인이 복합적으로 작용한 것으로 보입니다.",
                    directionStr, topCategory);
        }
    }

    private Map<String, Integer> sortByValueDesc(Map<String, Integer> map) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(map.entrySet());
        entries.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        Map<String, Integer> sorted = new LinkedHashMap<>();
        entries.forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }
}
