package sung.eco_analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

@Data
public class FrankfurterTimeSeriesResponse {
    private Double amount;
    private String base;

    @JsonProperty("start_date")
    private String startDate;

    @JsonProperty("end_date")
    private String endDate;

    // { "2026-06-09": { "KRW": 1378.5 }, ... }
    private Map<String, Map<String, Double>> rates;
}