package sung.eco_analysis.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import sung.eco_analysis.config.ApiProperties;
import sung.eco_analysis.dto.NaverNewsItem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Google Gemini으로 환율 뉴스 기사를 카테고리 분류한다.
 * 단순 키워드 부분문자열 매칭의 한계(부정문 오탐, 문맥 무시)를 보완한다.
 * <p>API 키가 설정되지 않으면 호출하지 않고 빈 결과를 반환해,
 * 호출 측({@link KeywordAnalysisService})이 기존 키워드 매칭으로 폴백하도록 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewsClassificationService {

    private final ApiProperties apiProperties;
    private final KeywordAnalysisService keywordAnalysisService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";
    // 1회 호출당 기사 수. 무료 등급은 '일일 요청 수'가 빡빡하므로 한 요청에 많이 담아 호출 횟수를 줄인다.
    // (maxOutputTokens는 설정하지 않아 모델 기본 최대치를 사용 → 100건 출력도 잘리지 않음)
    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRIES = 3;        // 5xx 시 추가 재시도 횟수 (총 4회 시도)
    private static final long RETRY_BACKOFF_MS = 2000;  // 선형 백오프: 2s, 4s, 6s

    private boolean enabled;
    private Set<String> validCategories;

    @PostConstruct
    void init() {
        validCategories = new HashSet<>(keywordAnalysisService.categoryNames());
        String key = apiProperties.getGemini().getApiKey();
        enabled = (key != null && !key.isBlank());
        if (enabled) {
            log.info("뉴스 LLM 분류 활성화 (Gemini, model={})", apiProperties.getGemini().getModel());
        } else {
            log.info("Gemini API 키 미설정 → 뉴스 LLM 분류 비활성화 (키워드 매칭 폴백)");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 기사들을 카테고리 분류한다. 반환 맵의 키는 입력 리스트의 인덱스.
     * 분류 비활성/실패 시 해당 인덱스는 맵에서 빠지고, 호출 측이 키워드 매칭으로 폴백한다.
     */
    public Map<Integer, List<String>> classify(List<NaverNewsItem> items) {
        Map<Integer, List<String>> out = new HashMap<>();
        if (!enabled || items.isEmpty()) return out;

        for (int start = 0; start < items.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, items.size());
            List<NaverNewsItem> slice = items.subList(start, end);
            try {
                List<ClassifiedItem> results = callGemini(slice);
                for (ClassifiedItem ci : results) {
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

    private List<ClassifiedItem> callGemini(List<NaverNewsItem> slice) throws Exception {
        String url = String.format("%s/%s:generateContent",
                BASE_URL, apiProperties.getGemini().getModel());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiProperties.getGemini().getApiKey().trim());

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", buildPrompt(slice))))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema()
                )
        );

        GeminiResponse resp = postWithRetry(url, new HttpEntity<>(body, headers));

        String json = extractText(resp);
        if (json == null || json.isBlank()) return List.of();
        // 구조화 출력은 최상위가 배열: [{index, categories}, ...]
        ClassifiedItem[] arr = objectMapper.readValue(json, ClassifiedItem[].class);
        return List.of(arr);
    }

    // Gemini는 부하 시 503(UNAVAILABLE)을 자주 반환한다. 일시적 5xx는 짧은 백오프로 재시도하고,
    // 4xx(인증·할당량 초과 등)는 즉시 던져 호출 측이 키워드 매칭으로 폴백하게 한다.
    private GeminiResponse postWithRetry(String url, HttpEntity<?> entity) {
        for (int attempt = 0; ; attempt++) {
            try {
                return restTemplate.exchange(url, HttpMethod.POST, entity, GeminiResponse.class).getBody();
            } catch (HttpServerErrorException e) {  // 5xx (503 high demand 등)
                if (attempt >= MAX_RETRIES) throw e;
                log.debug("Gemini {} → 재시도 {}/{}", e.getStatusCode(), attempt + 1, MAX_RETRIES);
                try {
                    Thread.sleep(RETRY_BACKOFF_MS * (attempt + 1));  // 선형 백오프
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    // 응답 JSON: candidates[0].content.parts[0].text
    private String extractText(GeminiResponse resp) {
        if (resp == null || resp.candidates == null || resp.candidates.isEmpty()) return null;
        GeminiResponse.Content content = resp.candidates.get(0).content;
        if (content == null || content.parts == null || content.parts.isEmpty()) return null;
        return content.parts.get(0).text;
    }

    // 응답을 [{index:INTEGER, categories:[STRING]}] 배열로 강제하는 스키마
    private Map<String, Object> responseSchema() {
        return Map.of(
                "type", "ARRAY",
                "items", Map.of(
                        "type", "OBJECT",
                        "properties", Map.of(
                                "index", Map.of("type", "INTEGER"),
                                "categories", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"))
                        ),
                        "required", List.of("index", "categories")
                )
        );
    }

    private String buildPrompt(List<NaverNewsItem> slice) {
        String categoryList = keywordAnalysisService.categoryNames().stream()
                .map(c -> "- " + c).collect(Collectors.joining("\n"));

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
        sb.append("\n각 기사의 index와 해당 카테고리 라벨 배열로 구성된 JSON 배열을 반환하라.");
        return sb.toString();
    }

    // 기사 1건의 분류 결과 (index = 입력 슬라이스 내 인덱스)
    public record ClassifiedItem(int index, List<String> categories) {}

    // ── Gemini generateContent 응답 (필요 필드만) ──
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GeminiResponse {
        public List<Candidate> candidates;

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Candidate {
            public Content content;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Content {
            public List<Part> parts;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Part {
            public String text;
        }
    }
}
