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
    // 키워드가 예측한 방향과 실제 변동 방향 일치 여부 (예측 불가 시 null)
    private Boolean matched;
}
