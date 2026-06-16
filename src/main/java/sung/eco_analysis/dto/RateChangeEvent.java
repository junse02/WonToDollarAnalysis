package sung.eco_analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class RateChangeEvent {
    private String date;
    private double rate;
    private double changeAmount;
    private double changePercent;
    private boolean rateUp;
    private List<String> relatedNewsTitles;
    private String topKeyword;
    private String analysis;
}
