package sung.eco_analysis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import sung.eco_analysis.config.ApiProperties;
import sung.eco_analysis.dto.NaverNewsApiResponse;
import sung.eco_analysis.dto.NaverNewsItem;

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

    public List<NaverNewsItem> fetchExchangeRateNews(int display) {
        String encodedQuery = URLEncoder.encode("달러 환율", StandardCharsets.UTF_8);
        String url = String.format("%s?query=%s&display=%d&sort=date", NAVER_NEWS_URL, encodedQuery, display);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Naver-Client-Id", apiProperties.getNaver().getClientId());
        headers.set("X-Naver-Client-Secret", apiProperties.getNaver().getClientSecret());

        try {
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<NaverNewsApiResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, NaverNewsApiResponse.class
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