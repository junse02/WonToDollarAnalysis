package sung.eco_analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

@Data
public class ExchangeRateApiResponse {

    private String result;

    @JsonProperty("conversion_rates")
    private Map<String, Double> conversionRates;

    @JsonProperty("time_last_update_utc")
    private String timeLastUpdateUtc;
}