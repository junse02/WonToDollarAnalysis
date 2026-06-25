package sung.eco_analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sung.eco_analysis.dto.StockQuote;
import sung.eco_analysis.entity.StockSnapshot;
import sung.eco_analysis.repository.StockSnapshotRepository;
import sung.eco_analysis.service.StockSnapshotService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockSnapshotServiceTest {

    @Mock StockSnapshotRepository snapshotRepository;
    @InjectMocks StockSnapshotService service;

    private StockSnapshot snap(String symbol, LocalDate date, Boolean predictedUp, double price) {
        return new StockSnapshot(symbol, "삼성전자", date, predictedUp == null ? 0 : (predictedUp ? 3 : -3),
                predictedUp, price);
    }

    private StockQuote quote(String symbol, double price, int sentimentScore) {
        return new StockQuote("삼성전자", symbol, price, 0.0, 0.0,
                List.of(), List.of(), "라벨", sentimentScore, List.of());
    }

    // 호재 우세 예측 후 이후 거래일 종가 상승 -> matched=true
    @Test
    void evaluate_predictUp_actualUp_matched() {
        StockSnapshot d0 = snap("005930.KS", LocalDate.of(2026, 6, 10), true, 70000);
        StockSnapshot d1 = snap("005930.KS", LocalDate.of(2026, 6, 11), true, 71000);  // +1.4%
        when(snapshotRepository.findByEvaluatedFalse()).thenReturn(List.of(d0, d1));
        when(snapshotRepository.findAll()).thenReturn(List.of(d0, d1));

        service.evaluatePending();

        assertThat(d0.isEvaluated()).isTrue();
        assertThat(d0.getActualUp()).isTrue();
        assertThat(d0.getMatched()).isTrue();
    }

    // 호재 예측인데 실제 하락 -> matched=false
    @Test
    void evaluate_predictUp_actualDown_notMatched() {
        StockSnapshot d0 = snap("005930.KS", LocalDate.of(2026, 6, 10), true, 70000);
        StockSnapshot d1 = snap("005930.KS", LocalDate.of(2026, 6, 11), true, 68000);  // -2.9%
        when(snapshotRepository.findByEvaluatedFalse()).thenReturn(List.of(d0, d1));
        when(snapshotRepository.findAll()).thenReturn(List.of(d0, d1));

        service.evaluatePending();

        assertThat(d0.isEvaluated()).isTrue();
        assertThat(d0.getActualUp()).isFalse();
        assertThat(d0.getMatched()).isFalse();
    }

    // 중립 예측(null)은 평가돼도 matched는 null (적중률 집계 제외)
    @Test
    void evaluate_neutral_matchedNull() {
        StockSnapshot d0 = snap("005930.KS", LocalDate.of(2026, 6, 10), null, 70000);
        StockSnapshot d1 = snap("005930.KS", LocalDate.of(2026, 6, 11), null, 71000);
        when(snapshotRepository.findByEvaluatedFalse()).thenReturn(List.of(d0, d1));
        when(snapshotRepository.findAll()).thenReturn(List.of(d0, d1));

        service.evaluatePending();

        assertThat(d0.isEvaluated()).isTrue();
        assertThat(d0.getMatched()).isNull();
    }

    // 이후 종가가 보합(±0.5% 미만)뿐이면 평가 보류
    @Test
    void evaluate_flat_staysPending() {
        StockSnapshot d0 = snap("005930.KS", LocalDate.of(2026, 6, 10), true, 70000);
        StockSnapshot d1 = snap("005930.KS", LocalDate.of(2026, 6, 11), true, 70100);  // +0.14%
        when(snapshotRepository.findByEvaluatedFalse()).thenReturn(List.of(d0, d1));
        when(snapshotRepository.findAll()).thenReturn(List.of(d0, d1));

        service.evaluatePending();

        assertThat(d0.isEvaluated()).isFalse();
        assertThat(d0.getMatched()).isNull();
    }

    // 캡처: 신규 종목은 감성 점수에 따른 예측 방향과 함께 저장된다
    @Test
    void capture_savesNewSnapshot_withPredictedDirection() {
        when(snapshotRepository.findBySymbolAndSnapshotDate(anyString(), any()))
                .thenReturn(Optional.empty());

        service.captureToday(List.of(quote("005930.KS", 70000, 3)));  // 호재 우세

        ArgumentCaptor<StockSnapshot> captor = ArgumentCaptor.forClass(StockSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        StockSnapshot saved = captor.getValue();
        assertThat(saved.getSymbol()).isEqualTo("005930.KS");
        assertThat(saved.getPrice()).isEqualTo(70000);
        assertThat(saved.getPredictedUp()).isTrue();
    }

    // 감성 점수 -> 예측 방향 매핑 (+2↑ 상승, -2↓ 하락, 그 사이 중립)
    @Test
    void predictedDirection_thresholds() {
        assertThat(StockSnapshotService.predictedDirection(2)).isTrue();
        assertThat(StockSnapshotService.predictedDirection(-2)).isFalse();
        assertThat(StockSnapshotService.predictedDirection(1)).isNull();
        assertThat(StockSnapshotService.predictedDirection(-1)).isNull();
    }

    // 감성 추이: 심볼별로 날짜순 점수 시계열을 만든다
    @Test
    void recentSentimentTrends_groupsBySymbolInDateOrder() {
        StockSnapshot a1 = new StockSnapshot("005930.KS", "삼성전자", LocalDate.of(2026, 6, 10), 1, null, 70000);
        StockSnapshot a2 = new StockSnapshot("005930.KS", "삼성전자", LocalDate.of(2026, 6, 11), 3, true, 71000);
        StockSnapshot b1 = new StockSnapshot("000660.KS", "SK하이닉스", LocalDate.of(2026, 6, 10), -2, false, 200000);
        when(snapshotRepository.findBySnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(any()))
                .thenReturn(List.of(a1, b1, a2));

        var trends = service.recentSentimentTrends(30);

        assertThat(trends).containsOnlyKeys("005930.KS", "000660.KS");
        assertThat(trends.get("005930.KS")).hasSize(2);
        assertThat(trends.get("005930.KS").get(0).score()).isEqualTo(1);
        assertThat(trends.get("005930.KS").get(1).score()).isEqualTo(3);
        assertThat(trends.get("000660.KS").get(0).label()).isEqualTo("06/10");
    }
}
