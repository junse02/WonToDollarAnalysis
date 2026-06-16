package sung.eco_analysis.dto;

import lombok.Data;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Data
public class NaverNewsItem {

    private String title;
    private String description;
    private String link;
    private String pubDate;
    private String originallink;

    public String getCleanTitle() {
        if (title == null) return "";
        return title.replaceAll("<[^>]+>", "").replaceAll("&quot;", "\"").replaceAll("&amp;", "&");
    }

    public String getCleanDescription() {
        if (description == null) return "";
        return description.replaceAll("<[^>]+>", "").replaceAll("&quot;", "\"").replaceAll("&amp;", "&");
    }

    // "Mon, 16 Jun 2026 10:00:00 +0900" → "2026년 06월 16일 10:00"
    public String getFormattedDate() {
        if (pubDate == null) return "";
        try {
            DateTimeFormatter inputFmt = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
            ZonedDateTime zdt = ZonedDateTime.parse(pubDate.trim(), inputFmt);
            return zdt.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH:mm"));
        } catch (Exception e) {
            return pubDate;
        }
    }

    public ZonedDateTime getParsedDate() {
        if (pubDate == null) return null;
        try {
            DateTimeFormatter inputFmt = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
            return ZonedDateTime.parse(pubDate.trim(), inputFmt);
        } catch (Exception e) {
            return null;
        }
    }
}