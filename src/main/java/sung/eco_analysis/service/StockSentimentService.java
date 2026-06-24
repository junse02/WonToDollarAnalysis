package sung.eco_analysis.service;

import org.springframework.stereotype.Service;
import sung.eco_analysis.dto.NaverNewsItem;

import java.util.List;
import java.util.Set;

/**
 * 종목 뉴스의 호재/악재 감성을 키워드 기반으로 산출한다. (무료·무한도)
 * 기사 1건이 호재 단어를 포함하면 +1, 악재 단어를 포함하면 -1로 집계.
 * <p>추후 Gemini 기반 감성 분류로 업그레이드할 수 있으나, 무료 등급 일일 한도(20/day)를
 * 환율 분류와 공유하므로 v1은 키워드 방식으로 둔다.
 */
@Service
public class StockSentimentService {

    private static final Set<String> POSITIVE = Set.of(
            "신고가", "급등", "상승", "강세", "호실적", "최대 실적", "사상 최대", "흑자", "호조",
            "수주", "계약", "목표가 상향", "상향", "매수", "성장", "개선", "돌파", "반등", "기대"
    );
    private static final Set<String> NEGATIVE = Set.of(
            "신저가", "급락", "하락", "약세", "부진", "적자", "감익", "어닝쇼크", "목표가 하향",
            "하향", "매도", "리콜", "소송", "악재", "우려", "악화", "손실", "하한가", "감소", "철수"
    );

    public Result analyze(List<NaverNewsItem> news) {
        int pos = 0, neg = 0;
        for (NaverNewsItem item : news) {
            String text = item.getCleanTitle() + " " + item.getCleanDescription();
            boolean hasPos = POSITIVE.stream().anyMatch(text::contains);
            boolean hasNeg = NEGATIVE.stream().anyMatch(text::contains);
            if (hasPos) pos++;
            if (hasNeg) neg++;
        }
        int score = pos - neg;
        String label;
        if (score >= 2) label = "호재 우세";
        else if (score <= -2) label = "악재 우세";
        else label = "중립";
        return new Result(label, score);
    }

    public record Result(String label, int score) {}
}
