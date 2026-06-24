package sung.eco_analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sung.eco_analysis.dto.NaverNewsItem;
import sung.eco_analysis.entity.NewsArticle;
import sung.eco_analysis.repository.NewsArticleRepository;
import sung.eco_analysis.service.NaverNewsService;
import sung.eco_analysis.service.NewsArchiveService;
import sung.eco_analysis.service.NewsClassificationService;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsArchiveServiceTest {

    @Mock NaverNewsService naverNewsService;
    @Mock NewsArticleRepository newsArticleRepository;
    @Mock NewsClassificationService newsClassificationService;

    @InjectMocks NewsArchiveService newsArchiveService;

    private NaverNewsItem news(String link) {
        NaverNewsItem item = new NaverNewsItem();
        item.setTitle("달러 환율");
        item.setDescription("내용");
        item.setLink(link);
        item.setPubDate("Wed, 17 Jun 2026 00:00:00 +0900");
        return item;
    }

    // 배치 내 중복(link)과 이미 DB에 있는 link를 모두 제외하고 신규만 저장한다
    @Test
    void ingest_savesOnlyNewArticles() {
        when(naverNewsService.fetchExchangeRateNews(100)).thenReturn(List.of(
                news("https://n.example/a"),
                news("https://n.example/b"),
                news("https://n.example/a"),  // 배치 내 중복
                news("https://n.example/c")
        ));
        // b는 이미 저장되어 있음
        when(newsArticleRepository.findExistingLinks(anyCollection()))
                .thenReturn(List.of("https://n.example/b"));

        int saved = newsArchiveService.ingest();

        assertThat(saved).isEqualTo(2);  // a, c
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NewsArticle>> captor = ArgumentCaptor.forClass(List.class);
        verify(newsArticleRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(NewsArticle::getLink)
                .containsExactlyInAnyOrder("https://n.example/a", "https://n.example/c");
    }

    // 새로 저장할 기사가 없으면 saveAll을 호출하지 않는다
    @Test
    void ingest_noNew_skipsSave() {
        when(naverNewsService.fetchExchangeRateNews(100)).thenReturn(List.of(news("https://n.example/a")));
        when(newsArticleRepository.findExistingLinks(anyCollection()))
                .thenReturn(List.of("https://n.example/a"));

        int saved = newsArchiveService.ingest();

        assertThat(saved).isZero();
        verify(newsArticleRepository, never()).saveAll(any());
    }

    // 빈 응답이면 dedup 쿼리도 저장도 하지 않는다
    @Test
    void ingest_emptyFetch_noop() {
        when(naverNewsService.fetchExchangeRateNews(100)).thenReturn(List.of());

        int saved = newsArchiveService.ingest();

        assertThat(saved).isZero();
        verify(newsArticleRepository, never()).findExistingLinks(anyCollection());
        verify(newsArticleRepository, never()).saveAll(any());
    }

    // 미분류 기사 백필: 분류 성공한 건만 categories 적용, 실패(누락)한 건은 미분류로 남는다
    @Test
    void reclassify_appliesLabelsToPending() {
        NewsArticle a = NewsArticle.from(news("https://n.example/a"));
        NewsArticle b = NewsArticle.from(news("https://n.example/b"));
        when(newsClassificationService.isEnabled()).thenReturn(true);
        when(newsArticleRepository.findByClassifiedFalseOrderByPublishedAtDesc(any(Pageable.class)))
                .thenReturn(List.of(a, b));
        // index 0만 분류 성공 (1은 503 등으로 누락)
        when(newsClassificationService.classify(anyList()))
                .thenReturn(Map.of(0, List.of("연준 긴축·금리 인상")));

        int n = newsArchiveService.reclassifyPending();

        assertThat(n).isEqualTo(1);
        assertThat(a.isClassified()).isTrue();
        assertThat(b.isClassified()).isFalse();  // 다음 주기에 재시도
        verify(newsArticleRepository).saveAll(anyList());
    }

    // 분류 비활성(키 없음)이면 조회조차 하지 않는다
    @Test
    void reclassify_disabled_noop() {
        when(newsClassificationService.isEnabled()).thenReturn(false);

        int n = newsArchiveService.reclassifyPending();

        assertThat(n).isZero();
        verify(newsArticleRepository, never()).findByClassifiedFalseOrderByPublishedAtDesc(any());
    }
}
