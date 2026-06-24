package sung.eco_analysis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import sung.eco_analysis.config.ApiProperties;
import sung.eco_analysis.dto.NaverNewsApiResponse;
import sung.eco_analysis.dto.NaverNewsItem;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NaverNewsService {

    private final RestTemplate restTemplate;
    private final ApiProperties apiProperties;

    private static final String NAVER_NEWS_URL = "https://openapi.naver.com/v1/search/news.json";

    @Cacheable(value = "news", key = "'fx|' + #display", unless = "#result == null || #result.isEmpty()")
    public List<NaverNewsItem> fetchExchangeRateNews(int display) {
        return doFetch("달러 환율", display);
    }

    // 임의 검색어로 뉴스 조회 (종목명 등). 외부 호출이라 캐시 프록시가 적용된다.
    @Cacheable(value = "news", key = "#query + '|' + #display", unless = "#result == null || #result.isEmpty()")
    public List<NaverNewsItem> fetchNews(String query, int display) {
        return doFetch(query, display);
    }

    private List<NaverNewsItem> doFetch(String query, int display) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = String.format("%s?query=%s&display=%d&sort=date", NAVER_NEWS_URL, encodedQuery, display);
        // 이미 인코딩된 문자열을 URI 객체로 넘겨 RestTemplate의 이중 인코딩 방지
        URI uri = URI.create(url);

        HttpHeaders headers = new HttpHeaders();
        // 시크릿/환경변수에 섞여 들어올 수 있는 앞뒤 공백·개행 제거 (HTTP 헤더 값에 \r\n 불가)
        headers.set("X-Naver-Client-Id", apiProperties.getNaver().getClientId().trim());
        headers.set("X-Naver-Client-Secret", apiProperties.getNaver().getClientSecret().trim());

        try {
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<NaverNewsApiResponse> response = restTemplate.exchange(
                    uri, HttpMethod.GET, entity, NaverNewsApiResponse.class
            );
            if (response.getBody() != null && response.getBody().getItems() != null) {
                List<NaverNewsItem> items = response.getBody().getItems();
                log.info("네이버 뉴스 조회 성공: {}건", items.size());
                if (!items.isEmpty()) {
                    log.info("최신 기사 pubDate: {}", items.get(0).getPubDate());
                }
                // 시스템 시계에 의존하지 않고, pubDate 최신순으로 정렬해 반환
                return sortByDateDesc(items);
            }
        } catch (Exception e) {
            log.error("네이버 뉴스 조회 실패 (HTTP {}): {}", e.getClass().getSimpleName(), e.getMessage());
        }
        return Collections.emptyList();
    }

    // 파싱된 pubDate 기준 최신순 정렬 (파싱 실패 항목은 뒤로)
    private List<NaverNewsItem> sortByDateDesc(List<NaverNewsItem> items) {
        return items.stream()
                .sorted(Comparator.comparing(
                        NaverNewsItem::getParsedDate,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .collect(Collectors.toList());
    }
}