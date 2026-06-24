package sung.eco_analysis.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sung.eco_analysis.config.ApiProperties;
import sung.eco_analysis.dto.NaverNewsItem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Claude(Anthropic)로 환율 뉴스 기사를 카테고리 분류한다.
 * 단순 키워드 부분문자열 매칭의 한계(부정문 오탐, 문맥 무시)를 보완한다.
 * <p>API 키가 설정되지 않으면 클라이언트를 만들지 않고 빈 결과를 반환해,
 * 호출 측({@link KeywordAnalysisService})이 기존 키워드 매칭으로 폴백하도록 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewsClassificationService {

    private final ApiProperties apiProperties;
    private final KeywordAnalysisService keywordAnalysisService;

    private AnthropicClient client;   // 키 없으면 null (분류 비활성)
    private Set<String> validCategories;

    private static final int BATCH_SIZE = 25;     // 1회 API 호출당 기사 수
    private static final long MAX_TOKENS = 4096L;  // 분류 출력은 작음 (비스트리밍)

    @PostConstruct
    void init() {
        validCategories = new HashSet<>(keywordAnalysisService.categoryNames());
        String key = apiProperties.getAnthropic().getApiKey();
        if (key == null || key.isBlank()) {
            log.info("Anthropic API 키 미설정 → 뉴스 LLM 분류 비활성화 (키워드 매칭 폴백)");
            return;
        }
        try {
            client = AnthropicOkHttpClient.builder().apiKey(key.trim()).build();
            log.info("뉴스 LLM 분류 활성화 (model={})", apiProperties.getAnthropic().getModel());
        } catch (Exception e) {
            log.warn("Anthropic 클라이언트 초기화 실패 → 키워드 매칭 폴백: {}", e.getMessage());
            client = null;
        }
    }

    public boolean isEnabled() {
        return client != null;
    }

    /**
     * 기사들을 카테고리 분류한다. 반환 맵의 키는 입력 리스트의 인덱스.
     * 분류 비활성/실패 시 해당 인덱스는 맵에서 빠지고, 호출 측이 키워드 매칭으로 폴백한다.
     */
    public Map<Integer, List<String>> classify(List<NaverNewsItem> items) {
        Map<Integer, List<String>> out = new HashMap<>();
        if (client == null || items.isEmpty()) return out;

        for (int start = 0; start < items.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, items.size());
            List<NaverNewsItem> slice = items.subList(start, end);
            try {
                ClassificationResult res = callApi(slice);
                if (res == null || res.articles() == null) continue;
                for (ClassifiedItem ci : res.articles()) {
                    int local = ci.index();
                    if (local < 0 || local >= slice.size()) continue;  // 모델 인덱스 방어
                    List<String> valid = (ci.categories() == null) ? List.of()
                            : ci.categories().stream()
                                .filter(validCategories::contains)  // 허용 카테고리만
                                .distinct()
                                .collect(Collectors.toList());
                    out.put(start + local, valid);
                }
            } catch (Exception e) {
                log.warn("뉴스 분류 실패 (배치 {}~{}): {}", start, end, e.getMessage());
            }
        }
        return out;
    }

    private ClassificationResult callApi(List<NaverNewsItem> slice) {
        String prompt = buildPrompt(slice);
        StructuredMessageCreateParams<ClassificationResult> params = MessageCreateParams.builder()
                .model(apiProperties.getAnthropic().getModel())
                .maxTokens(MAX_TOKENS)
                .outputConfig(ClassificationResult.class)
                .addUserMessage(prompt)
                .build();

        return client.messages().create(params).content().stream()
                .flatMap(cb -> cb.text().stream())
                .map(t -> t.text())   // 구조화 출력 → 타입드 결과
                .findFirst()
                .orElse(null);
    }

    private String buildPrompt(List<NaverNewsItem> slice) {
        String categoryList = String.join("\n", keywordAnalysisService.categoryNames().stream()
                .map(c -> "- " + c).collect(Collectors.toList()));

        StringBuilder sb = new StringBuilder();
        sb.append("다음 한국어 뉴스(원/달러 환율 관련)를 환율 변동 '원인' 카테고리로 분류하라.\n");
        sb.append("아래 라벨만 사용하고, 기사 내용이 명확히 뒷받침하는 카테고리만 고른다.\n");
        sb.append("부정·해소 문맥(예: '금리 인상 우려 해소', '침체 가능성 낮다')이면 해당 카테고리를 넣지 않는다.\n");
        sb.append("한 기사가 여러 카테고리에 해당하거나, 해당이 없으면 빈 배열로 둔다.\n\n");
        sb.append("카테고리 목록:\n").append(categoryList).append("\n\n");
        sb.append("기사 (index: 제목 — 요약):\n");
        for (int i = 0; i < slice.size(); i++) {
            NaverNewsItem item = slice.get(i);
            sb.append(i).append(": ")
              .append(item.getCleanTitle()).append(" — ")
              .append(item.getCleanDescription()).append("\n");
        }
        sb.append("\n각 기사 index와 해당 카테고리 라벨 배열을 반환하라.");
        return sb.toString();
    }

    // ── 구조화 출력 스키마 ──
    // 기사 1건의 분류 결과 (index = 입력 슬라이스 내 인덱스)
    public record ClassifiedItem(int index, List<String> categories) {}

    // 배치 전체 분류 결과
    public record ClassificationResult(List<ClassifiedItem> articles) {}
}
