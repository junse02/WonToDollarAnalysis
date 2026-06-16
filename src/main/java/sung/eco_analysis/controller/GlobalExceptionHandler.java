package sung.eco_analysis.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 화면(View) 컨트롤러에서 처리되지 않은 예외를 친절한 에러 페이지로 렌더링.
 * 스택트레이스를 사용자에게 노출하지 않는다.
 */
@ControllerAdvice(assignableTypes = WebController.class)
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e, Model model) {
        log.error("페이지 렌더링 중 오류", e);
        model.addAttribute("message", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        return "error";
    }
}
