package sung.eco_analysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EcoAnalysisApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcoAnalysisApplication.class, args);
    }
}