package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailSendExceptionHandlerTest {

    @RestController
    static class FailingController {
        @GetMapping("/fail")
        void fail() {
            throw new EmailSendException("Não foi possível enviar o e-mail. Tente novamente.");
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void emailSendFailure_isBadGatewayWithErrorResponse() throws Exception {
        mockMvc.perform(get("/fail"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("Não foi possível enviar o e-mail. Tente novamente."));
    }
}
