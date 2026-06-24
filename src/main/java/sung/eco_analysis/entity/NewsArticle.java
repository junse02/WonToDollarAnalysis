package sung.eco_analysis.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sung.eco_analysis.dto.NaverNewsItem;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    // LLM 분류 여부. true면 categories(빈 문자열 포함)를 신뢰해 분석에 사용한다.
    private boolean classified = false;

    // 분류된 카테고리 키들을 '|'로 연결한 문자열 (분류했으나 해당 없으면 빈 문자열)
    @Column(length = 1000)
    private String categories;

    private static final String CATEGORY_DELIM = "|";

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

    /** LLM 분류 결과를 적용한다. (빈 목록이면 '해당 카테고리 없음'으로 분류 완료 처리) */
    public void applyCategories(List<String> cats) {
        this.categories = (cats == null) ? "" : String.join(CATEGORY_DELIM, cats);
        this.classified = true;
    }

    /** 영속 엔티티 → 분석용 DTO. 원본 필드를 그대로 복원해 기존 분석 코드를 변경 없이 재사용한다. */
    public NaverNewsItem toItem() {
        NaverNewsItem item = new NaverNewsItem();
        item.setTitle(title);
        item.setDescription(description);
        item.setLink(link);
        item.setOriginallink(originallink);
        item.setPubDate(pubDate);
        if (classified) {
            // 분류 완료분만 카테고리 주입 (미분류는 null → 키워드 매칭 폴백)
            Set<String> set = new LinkedHashSet<>();
            if (categories != null && !categories.isBlank()) {
                Arrays.stream(categories.split("\\" + CATEGORY_DELIM))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(set::add);
            }
            item.setCategories(set);
        }
        return item;
    }
}
