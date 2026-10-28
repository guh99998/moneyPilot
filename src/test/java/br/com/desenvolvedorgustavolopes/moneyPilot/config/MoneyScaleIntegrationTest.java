package br.com.desenvolvedorgustavolopes.moneyPilot.config;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 011, CA7: todo campo de dinheiro recusa mais de 2 casas e vírgula, com erro no campo. */
class MoneyScaleIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "minha frase secreta";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String accessToken;

    @BeforeEach
    void signIn() throws Exception {
        String email = "money-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, PASSWORD, "Money User"))))
                .andExpect(status().isAccepted());
        markVerified(email);
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.token");
    }

    static Stream<Arguments> moneyFields() {
        return Stream.of(
                Arguments.of("/api/v1/accounts", "initialBalance",
                        "{\"name\":\"Carteira\",\"accountType\":\"CHECKING\",\"initialBalance\":%s}"),
                Arguments.of("/api/v1/transactions", "amount",
                        "{\"accountId\":1,\"categoryId\":1,\"amount\":%s,\"type\":\"EXPENSE\",\"date\":\"2026-10-28\"}"),
                Arguments.of("/api/v1/transactions/transfers", "amount",
                        "{\"fromAccountId\":1,\"toAccountId\":2,\"amount\":%s,\"date\":\"2026-10-28\"}"),
                Arguments.of("/api/v1/bills", "amount",
                        "{\"categoryId\":1,\"amount\":%s,\"dueDate\":\"2026-11-10\",\"type\":\"PAYABLE\"}"),
                Arguments.of("/api/v1/bill-recurrences", "amount",
                        "{\"categoryId\":1,\"amount\":%s,\"type\":\"PAYABLE\",\"dayOfMonth\":10,\"startDate\":\"2026-11-01\"}"),
                Arguments.of("/api/v1/bills/1/settle", "amount",
                        "{\"accountId\":1,\"amount\":%s}"),
                Arguments.of("/api/v1/budgets", "amountLimit",
                        "{\"categoryId\":1,\"amountLimit\":%s,\"month\":10,\"year\":2026}")
        );
    }

    @ParameterizedTest(name = "{0} {1}: 10.555 is rejected")
    @MethodSource("moneyFields")
    void moreThanTwoDecimals_isRejectedOnTheField(String path, String field, String template) throws Exception {
        mockMvc.perform(post(path).header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(template.formatted("10.555")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors." + field).isNotEmpty());
    }

    @ParameterizedTest(name = "{0} {1}: \"10,50\" is rejected")
    @MethodSource("moneyFields")
    void commaValue_isRejectedOnTheField(String path, String field, String template) throws Exception {
        mockMvc.perform(post(path).header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(template.formatted("\"10,50\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors." + field).isNotEmpty());
    }

    @ParameterizedTest(name = "{0} {1}: 10.50 passes validation")
    @MethodSource("moneyFields")
    void twoDecimals_passesValidation(String path, String field, String template) throws Exception {
        MvcResult result = mockMvc.perform(post(path).header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(template.formatted("10.50")))
                .andReturn();

        // Os ids do exemplo não existem, então pode haver 404 ou erro de regra de negócio em outro campo;
        // o que importa é o campo de dinheiro não estar entre os erros.
        String body = result.getResponse().getContentAsString();
        if (result.getResponse().getStatus() == 400 && body.contains("fieldErrors\":{")) {
            assertFalse(body.contains("\"" + field + "\":"), body);
        }
    }
}
