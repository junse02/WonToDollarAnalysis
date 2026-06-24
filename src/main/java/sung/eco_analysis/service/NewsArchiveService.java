package sung.eco_analysis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sung.eco_analysis.dto.NaverNewsItem;
import sung.eco_analysis.entity.NewsArticle;
import sung.eco_analysis.repository.NewsArticleRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 뉴스 영속화 계층. 네이버 API에서 받은 기사를 DB에 누적 저장(link 기준 dedup)하고,
 * 분석 코드에는 DB에서 복원한 {@link NaverNewsItem} 목록을 제공한다.
 * <p>API는 최근 기사만 주지만 DB는 계속 쌓이므로, 적중률·변동 원인 분석이
 * API 윈도우(보통 최근 수일)를 넘어 장기 히스토리를 활용할 수 있다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewsArchiveService {

    private final NaverNewsService naverNewsService;
    private final NewsArticleRepository newsArticleRepository;
    private final NewsClassificationService newsClassificationService;

    private static final int FETCH_SIZE = 100;  // 네이버 API 1회 최대 조회 건수

    /**
     * 네이버 API에서 최신 기사를 가져와 미저장 건만 DB에 추가한다. (link 기준 배치 dedup)
     * @return 새로 저장된 기사 수
     */
    @Transactional
    public int ingest() {
        List<NaverNewsItem> fresh = naverNewsService.fetchExchangeRateNews(FETCH_SIZE);
        if (fresh.isEmpty()) return 0;

        // 배치 내 중복 link 제거 (link 우선)
        Map<String, NaverNewsItem> byLink = new LinkedHashMap<>();
        for (NaverNewsItem item : fresh) {
            if (item.getLink() != null && !item.getLink().isBlank()) {
                byLink.putIfAbsent(item.getLink(), item);
            }
        }
        if (byLink.isEmpty()) return 0;

        // 이미 저장된 link 한 번에 조회 후 신규만 추림
        Set<String> existing = new HashSet<>(newsArticleRepository.findExistingLinks(byLink.keySet()));
        List<NaverNewsItem> newItems = byLink.entrySet().stream()
                .filter(e -> !existing.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
        if (newItems.isEmpty()) return 0;

        // 신규 기사만 LLM 분류 (비활성/실패 시 빈 맵 → classified=false로 키워드 폴백)
        Map<Integer, List<String>> labels = newsClassificationService.classify(newItems);

        List<NewsArticle> toSave = new ArrayList<>(newItems.size());
        for (int i = 0; i < newItems.size(); i++) {
            NewsArticle article = NewsArticle.from(newItems.get(i));
            List<String> cats = labels.get(i);
            if (cats != null) article.applyCategories(cats);
            toSave.add(article);
        }

        newsArticleRepository.saveAll(toSave);
        log.info("뉴스 아카이브: 신규 {}건 저장 (조회 {}건, 분류 {}건)",
                toSave.size(), byLink.size(), labels.size());
        return toSave.size();
    }

    /** 현재 시점 분석용: 발행 시각 최신순 limit건. (DB가 비어 있으면 빈 목록) */
    public List<NaverNewsItem> getLatestNews(int limit) {
        return newsArticleRepository.findByOrderByPublishedAtDesc(PageRequest.of(0, limit)).stream()
                .map(NewsArticle::toItem)
                .collect(Collectors.toList());
    }

    /** 히스토리 분석용: 최근 days일치 전체 기사 (발행 시각 최신순). */
    public List<NaverNewsItem> getRecentNews(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return newsArticleRepository.findByPublishedAtAfterOrderByPublishedAtDesc(cutoff).stream()
                .map(NewsArticle::toItem)
                .collect(Collectors.toList());
    }
}
