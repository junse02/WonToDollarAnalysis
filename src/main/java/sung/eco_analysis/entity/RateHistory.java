package sung.eco_analysis.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "rate_history")
@Data
@NoArgsConstructor
public class RateHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String currency;
    private Double rate;
    private LocalDateTime recordedAt;

    public RateHistory(String currency, Double rate, LocalDateTime recordedAt) {
        this.currency = currency;
        this.rate = rate;
        this.recordedAt = recordedAt;
    }
}