package sung.eco_analysis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "api")
@Getter
@Setter
public class ApiProperties {

    private Naver naver = new Naver();
    private ExchangeRate exchangeRate = new ExchangeRate();

    @Getter
    @Setter
    public static class Naver {
        private String clientId;
        private String clientSecret;
    }

    @Getter
    @Setter
    public static class ExchangeRate {
        private String key;
    }
}