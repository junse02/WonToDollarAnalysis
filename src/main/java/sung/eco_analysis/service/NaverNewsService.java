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
import java.time.ZonedDateTime;
import java.util.Collections;
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
                // 최근 7일 이내 기사만 필터링, 그래도 없으면 전체 반환
                List<NaverNewsItem> recent = filterRecent(items, 7);
                return recent.isEmpty() ? items : recent;
            }
        } catch (Exception e) {
            log.error("네이버 뉴스 조회 실패 (HTTP {}): {}", e.getClass().getSimpleName(), e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<NaverNewsItem> filterRecent(List<NaverNewsItem> items, int days) {
        ZonedDateTime cutoff = ZonedDateTime.now().minusDays(days);
        return items.stream()
                .filter(item -> {
                    ZonedDateTime date = item.getParsedDate();
                    return date != null && date.isAfter(cutoff);
                })
                .collect(Collectors.toList());
    }
}