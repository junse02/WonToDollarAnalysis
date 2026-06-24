package sung.eco_analysis.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sung.eco_analysis.dto.NaverNewsItem;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 수집한 환율 뉴스 기사 1건. link 기준으로 중복 제거해 누적 저장한다.
 * 네이버 뉴스 API는 최근 기사만 제공하므로, 매 수집 시 DB에 영속화해
 * 시간이 지날수록 더 긴 분석 히스토리(부트스트랩·변동 원인·압력 지수)를 확보한다.
 */
@Entity
@Table(name = "news_article",
        uniqueConstraints = @UniqueConstraint(columnNames = "link"),
        indexes = @Index(name = "idx_news_published_at", columnList = "publishedAt"))
@Getter
@Setter
@NoArgsConstructor
public class NewsArticle {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 600)
    private String title;

    @Column(length = 1200)
    private String description;

    // 중복 제거 기준 키 (네이버 기사 링크)
    @Column(length = 800, nullable = false)
    private String link;

    @Column(length = 800)
    private String originallink;

    // 원본 pubDate 문자열 ("Mon, 16 Jun 2026 10:00:00 +0900") - 화면 표시·재파싱용으로 보존
    @Column(length = 64)
    private String pubDate;

    // pubDate를 KST 기준으로 정규화한 발행 시각 (날짜 범위 조회용). 파싱 실패 시 수집 시각으로 대체.
    private LocalDateTime publishedAt;

    // DB 저장 시각
    private LocalDateTime collectedAt;

    /** 네이버 API DTO → 영속 엔티티. 원본 필드를 손실 없이 보존해 분석 로직과 호환된다. */
    public static NewsArticle from(NaverNewsItem item) {
        NewsArticle a = new NewsArticle();
        a.title = item.getTitle();
        a.description = item.getDescription();
        a.link = item.getLink();
        a.originallink = item.getOriginallink();
        a.pubDate = item.getPubDate();

        LocalDateTime now = LocalDateTime.now();
        ZonedDateTime parsed = item.getParsedDate();
        a.publishedAt = (parsed != null)
                ? parsed.withZoneSameInstant(KST).toLocalDateTime()
                : now;
        a.collectedAt = now;
        return a;
    }

    /** 영속 엔티티 → 분석용 DTO. 원본 필드를 그대로 복원해 기존 분석 코드를 변경 없이 재사용한다. */
    public NaverNewsItem toItem() {
        NaverNewsItem item = new NaverNewsItem();
        item.setTitle(title);
        item.setDescription(description);
        item.setLink(link);
        item.setOriginallink(originallink);
        item.setPubDate(pubDate);
        return item;
    }
}
