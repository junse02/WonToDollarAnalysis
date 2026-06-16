package sung.eco_analysis.service;

import org.springframework.stereotype.Service;
import sung.eco_analysis.dto.NaverNewsItem;

import java.util.*;

@Service
public class KeywordAnalysisService {

    // 카테고리별 키워드 정의
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
                "수출 증가", "무역흑자", "경상수지 흑자", "수출 호조", "경상흑자", "수출 증가"
        ));
        KEYWORD_CATEGORIES.put("무역·수입 압박", Arrays.asList(
                "무역적자", "수입 증가", "경상수지 적자", "수출 감소", "경상적자", "무역 불균형"
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

    public Map<String, Integer> analyzeKeywords(List<NaverNewsItem> newsItems) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String category : KEYWORD_CATEGORIES.keySet()) {
            counts.put(category, 0);
        }

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
        int rateUp = keywords.getOrDefault("연준 긴축·금리 인상", 0)
                + keywords.getOrDefault("달러 강세", 0)
                + keywords.getOrDefault("지정학 리스크", 0)
                + keywords.getOrDefault("무역·수입 압박", 0);

        int rateDown = keywords.getOrDefault("연준 완화·금리 인하", 0)
                + keywords.getOrDefault("달러 약세", 0)
                + keywords.getOrDefault("무역·수출 호조", 0)
                + keywords.getOrDefault("외국인 자금 유입", 0);

        String trend;
        if (rateUp > rateDown) {
            trend = "달러 강세(원화 약세) 요인이 우세합니다.";
        } else if (rateDown > rateUp) {
            trend = "달러 약세(원화 강세) 요인이 우세합니다.";
        } else {
            trend = "방향성이 혼재되어 있습니다.";
        }

        String topKeyword = getTopKeyword(keywords);
        return String.format("최근 뉴스 분석 결과, '%s' 관련 기사가 가장 많습니다. %s", topKeyword, trend);
    }

    private Map<String, Integer> sortByValueDesc(Map<String, Integer> map) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(map.entrySet());
        entries.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        Map<String, Integer> sorted = new LinkedHashMap<>();
        entries.forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }
}