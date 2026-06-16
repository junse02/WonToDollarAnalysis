package sung.eco_analysis.dto;

import lombok.Data;
import java.util.List;

@Data
public class NaverNewsApiResponse {

    private int total;
    private int start;
    private int display;
    private List<NaverNewsItem> items;
}