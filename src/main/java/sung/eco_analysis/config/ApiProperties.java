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
    private Anthropic anthropic = new Anthropic();

    @Getter
    @Setter
    public static class Naver {
        private String clientId;
        private String clientSecret;
    }

    @Getter
    @Setter
    public static class Anthropic {
        // 비어 있으면 LLM 분류 비활성화 (키워드 매칭으로 폴백)
        private String apiKey;
        private String model = "claude-opus-4-8";
    }
}