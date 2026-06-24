package sung.eco_analysis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import sung.eco_analysis.dto.StockQuote;
import sung.eco_analysis.service.StockService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class StockWebController {

    private final StockService stockService;

    @GetMapping("/stocks")
    public String stocks(Model model) {
        List<StockQuote> stocks = stockService.getTopStocks();
        boolean anyAvailable = stocks.stream().anyMatch(StockQuote::isAvailable);

        model.addAttribute("stocks", stocks);
        model.addAttribute("anyAvailable", anyAvailable);
        model.addAttribute("lastUpdated",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        return "stocks";
    }
}
