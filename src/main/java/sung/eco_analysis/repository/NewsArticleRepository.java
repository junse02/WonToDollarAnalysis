package sung.eco_analysis.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sung.eco_analysis.entity.NewsArticle;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    // 발행 시각 기준 최신순 N건 (현재 시점 뉴스 키워드/압력지수용). Pageable로 개수 제한.
    List<NewsArticle> findByOrderByPublishedAtDesc(Pageable pageable);

    // 최근 N일치 (부트스트랩·날짜별 변동 원인 분석용)
    List<NewsArticle> findByPublishedAtAfterOrderByPublishedAtDesc(LocalDateTime after);

    // 배치 dedup: 주어진 link 중 이미 저장된 것만 반환 (건별 existsByLink 대신 1회 쿼리)
    @Query("select n.link from NewsArticle n where n.link in :links")
    List<String> findExistingLinks(@Param("links") Collection<String> links);
}
