package sung.eco_analysis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sung.eco_analysis.dto.StockQuote;
import sung.eco_analysis.service.StockService;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @GetMapping("/stocks")
    public ResponseEntity<List<StockQuote>> getTopStocks() {
        return ResponseEntity.ok(stockService.getTopStocks());
    }

    @GetMapping("/stocks/us")
    public ResponseEntity<List<StockQuote>> getTopUsStocks() {
        return ResponseEntity.ok(stockService.getTopUsStocks());
    }
}
