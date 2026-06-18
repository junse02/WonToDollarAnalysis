package sung.eco_analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sung.eco_analysis.entity.DailySnapshot;
import sung.eco_analysis.entity.RateHistory;
import sung.eco_analysis.repository.DailySnapshotRepository;
import sung.eco_analysis.service.ExchangeRateService;
import sung.eco_analysis.service.KeywordAnalysisService;
import sung.eco_analysis.service.NaverNewsService;
import sung.eco_analysis.service.SnapshotService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapshotServiceTest {

    @Mock DailySnapshotRepository snapshotRepository;
    @Mock ExchangeRateService exchangeRateService;
    @Mock NaverNewsService naverNewsService;
    @Mock KeywordAnalysisService keywordAnalysisService;

    @InjectMocks SnapshotService snapshotService;

    // 다음 날 환율이 오르고 예측이 '강세(상승)'면 matched=true
    @Test
    void evaluate_predictUp_actualUp_matched() {
        DailySnapshot snap = new DailySnapshot(LocalDate.of(2026, 6, 10), 50, true, 1500.0);
        when(snapshotRepository.findByEvaluatedFalse()).thenReturn(List.of(snap));
        when(exchangeRateService.getRecentHistory(90)).thenReturn(List.of(
                new RateHistory("USD/KRW", 1500.0, LocalDateTime.of(2026, 6, 10, 12, 0)),
                new RateHistory("USD/KRW", 1520.0, LocalDateTime.of(2026, 6, 11, 12, 0))
        ));

        snapshotService.evaluatePending();

        assertThat(snap.isEvaluated()).isTrue();
        assertThat(snap.getActualUp()).isTrue();
        assertThat(snap.getMatched()).isTrue();
    }

    // 예측은 '강세'인데 실제로는 하락 -> matched=false
    @Test
    void evaluate_predictUp_actualDown_notMatched() {
        DailySnapshot snap = new DailySnapshot(LocalDate.of(2026, 6, 10), 50, true, 1500.0);
        when(snapshotRepository.findByEvaluatedFalse()).thenReturn(List.of(snap));
        when(exchangeRateService.getRecentHistory(90)).thenReturn(List.of(
                new RateHistory("USD/KRW", 1500.0, LocalDateTime.of(2026, 6, 10, 12, 0)),
                new RateHistory("USD/KRW", 1480.0, LocalDateTime.of(2026, 6, 11, 12, 0))
        ));

        snapshotService.evaluatePending();

        assertThat(snap.isEvaluated()).isTrue();
        assertThat(snap.getActualUp()).isFalse();
        assertThat(snap.getMatched()).isFalse();
    }

    // 중립 예측(null)은 평가되더라도 matched는 null (적중률 집계 제외)
    @Test
    void evaluate_neutralPrediction_matchedNull() {
        DailySnapshot snap = new DailySnapshot(LocalDate.of(2026, 6, 10), 0, null, 1500.0);
        when(snapshotRepository.findByEvaluatedFalse()).thenReturn(List.of(snap));
        when(exchangeRateService.getRecentHistory(90)).thenReturn(List.of(
                new RateHistory("USD/KRW", 1500.0, LocalDateTime.of(2026, 6, 10, 12, 0)),
                new RateHistory("USD/KRW", 1520.0, LocalDateTime.of(2026, 6, 11, 12, 0))
        ));

        snapshotService.evaluatePending();

        assertThat(snap.isEvaluated()).isTrue();
        assertThat(snap.getMatched()).isNull();
    }

    // 부트스트랩: 뉴스 윈도우의 과거 날짜를 소급해 스냅샷을 생성한다
    @Test
    void bootstrap_createsPastSnapshot_fromNewsWindow() {
        LocalDate past = LocalDate.now().minusDays(2);
        LocalDate next = LocalDate.now().minusDays(1);

        when(naverNewsService.fetchExchangeRateNews(100)).thenReturn(List.of());
        when(keywordAnalysisService.computeHistoricalPressureIndex(anyList()))
                .thenReturn(Map.of(past, 50));
        when(keywordAnalysisService.predictedDirection(50)).thenReturn(true);
        when(exchangeRateService.getRecentHistory(90)).thenReturn(List.of(
                new RateHistory("USD/KRW", 1500.0, past.atTime(12, 0)),
                new RateHistory("USD/KRW", 1520.0, next.atTime(12, 0))
        ));
        when(snapshotRepository.existsBySnapshotDate(past)).thenReturn(false);
        when(snapshotRepository.findByEvaluatedFalse()).thenReturn(List.of());

        snapshotService.bootstrapFromHistory();

        ArgumentCaptor<DailySnapshot> captor = ArgumentCaptor.forClass(DailySnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        DailySnapshot saved = captor.getValue();
        assertThat(saved.getSnapshotDate()).isEqualTo(past);
        assertThat(saved.getPressureIndex()).isEqualTo(50);
        assertThat(saved.getPredictedUp()).isTrue();
        assertThat(saved.getRate()).isEqualTo(1500.0);
    }

    // 부트스트랩: 이미 라이브 스냅샷이 있는 날짜는 덮어쓰지 않는다
    @Test
    void bootstrap_skipsExistingSnapshotDate() {
        LocalDate past = LocalDate.now().minusDays(2);

        when(naverNewsService.fetchExchangeRateNews(100)).thenReturn(List.of());
        when(keywordAnalysisService.computeHistoricalPressureIndex(anyList()))
                .thenReturn(Map.of(past, 50));
        when(exchangeRateService.getRecentHistory(90)).thenReturn(List.of(
                new RateHistory("USD/KRW", 1500.0, past.atTime(12, 0))
        ));
        when(snapshotRepository.existsBySnapshotDate(past)).thenReturn(true);

        snapshotService.bootstrapFromHistory();

        verify(snapshotRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    // 다음 날 환율이 아직 없으면 평가 보류 (evaluated=false 유지)
    @Test
    void evaluate_noLaterRate_pending() {
        DailySnapshot snap = new DailySnapshot(LocalDate.of(2026, 6, 10), 50, true, 1500.0);
        when(snapshotRepository.findByEvaluatedFalse()).thenReturn(List.of(snap));
        when(exchangeRateService.getRecentHistory(90)).thenReturn(List.of(
                new RateHistory("USD/KRW", 1500.0, LocalDateTime.of(2026, 6, 10, 12, 0))
        ));

        snapshotService.evaluatePending();

        assertThat(snap.isEvaluated()).isFalse();
        assertThat(snap.getMatched()).isNull();
    }
}
