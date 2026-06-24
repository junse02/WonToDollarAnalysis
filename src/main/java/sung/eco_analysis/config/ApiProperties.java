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
    private Gemini gemini = new Gemini();

    @Getter
    @Setter
    public static class Naver {
        private String clientId;
        private String clientSecret;
    }

    @Getter
    @Setter
    public static class Gemini {
        // 비어 있으면 LLM 분류 비활성화 (키워드 매칭으로 폴백)
        private String apiKey;
        // 무료 등급에서 동작하는 flash 기본값. pro(gemini-2.5-pro)는 결제 활성화 필요.
        private String model = "gemini-2.5-flash";
    }
}