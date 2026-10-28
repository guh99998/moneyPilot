package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.EmailMessage;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.RecordingEmailSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A política de senha nova vale igual no cadastro, na troca e no reset (spec 009). Cada regra é conferida nas três
 * rotas, e quem já tinha senha curta continua entrando.
 */
class PasswordPolicyIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "minha frase secreta";
    private static final String NAME = "Maria Silva";
    private static final String LENGTH_MESSAGE = "A senha precisa ter de 10 a 64 caracteres.";
    private static final String PERSONAL_MESSAGE = "A senha não pode conter seu e-mail nem seu nome.";
    private static final Pattern TOKEN = Pattern.compile("token=(\\S+)");
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingEmailSender emailSender;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        emailSender.clear();
    }

    // CA1

    @Test
    void aPasswordWithFewerThanTenCharacters_isRefusedOnEveryRoute() throws Exception {
        assertRefusedEverywhere("curta1234", LENGTH_MESSAGE);
    }

    @Test
    void aPasswordWithExactlyTenCharacters_isAccepted() throws Exception {
        assertAcceptedEverywhere("dez letras");
    }

    // CA2

    @Test
    void aLeakedPassword_isRefusedOnEveryRoute_whateverTheCase() throws Exception {
        assertRefusedEverywhere("senha12345", NewPasswordValidator.LEAKED_MESSAGE);
        assertRefusedEverywhere("SENHA12345", NewPasswordValidator.LEAKED_MESSAGE);
        assertRefusedEverywhere("PassWord123", NewPasswordValidator.LEAKED_MESSAGE);
        assertRefusedEverywhere("MinhaSenha", NewPasswordValidator.LEAKED_MESSAGE);
        // Quem está na lista de vazadas é recusada como vazada, mesmo sendo também uma sequência.
        assertRefusedEverywhere("1234567890", NewPasswordValidator.LEAKED_MESSAGE);
    }

    @Test
    void aSequenceOrARepetition_isRefusedOnEveryRoute() throws Exception {
        assertRefusedEverywhere("2345678901", NewPasswordValidator.SEQUENCE_MESSAGE);
        assertRefusedEverywhere("aaaaaaaaaa", NewPasswordValidator.SEQUENCE_MESSAGE);
        assertRefusedEverywhere("BCDEFGHIJK", NewPasswordValidator.SEQUENCE_MESSAGE);
        assertRefusedEverywhere("8765432109", NewPasswordValidator.SEQUENCE_MESSAGE);
        assertRefusedEverywhere("BBBBBBBBBB", NewPasswordValidator.SEQUENCE_MESSAGE);
    }

    @Test
    void theMessageForALeakedPassword_saysItComesFromPublicLists_notFromThisApp() {
        assertTrue(NewPasswordValidator.LEAKED_MESSAGE.contains("listas públicas de senhas vazadas"));
    }

    // CA3

    @Test
    void aPasswordWithTheEmail_isRefusedOnEveryRoute() throws Exception {
        assertRefusedEverywhere(address -> "ola " + address + " ola", PERSONAL_MESSAGE);
    }

    @Test
    void aPasswordWithTheEmailLocalPart_isRefusedOnEveryRoute() throws Exception {
        assertRefusedEverywhere(address -> "xx" + address.substring(0, address.indexOf('@')).toUpperCase() + "xx",
                PERSONAL_MESSAGE);
    }

    @Test
    void aPasswordWithAPartOfTheName_isRefusedOnEveryRoute_ignoringCaseAndAccents() throws Exception {
        // O nome do cadastro é "Maria Silva".
        assertRefusedEverywhere("meu cachorro é o SILVA", PERSONAL_MESSAGE);
        assertRefusedEverywhere("amor da MÁRIA", PERSONAL_MESSAGE);
    }

    @Test
    void aNameWithAccents_isMatchedByThePasswordWithoutThem() throws Exception {
        String email = newAddress();

        register(email, "tudo sobre joao", "João Ferreira")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value(PERSONAL_MESSAGE));
    }

    @Test
    void aShortPartOfTheName_doesNotCount() throws Exception {
        // "Ana" tem menos de 4 letras: a senha pode ter "ana".
        register(newAddress(), "banana na janela", "Ana Luz").andExpect(status().isAccepted());
    }

    @Test
    void registeringWithTheirOwnEmail_isRefusedTheSameWhetherOrNotTheAccountExists() throws Exception {
        String taken = registerVerified();
        String free = newAddress();

        MvcResult existing = register(taken, "ola " + taken + " ola", NAME).andReturn();
        MvcResult unused = register(free, "ola " + free + " ola", NAME).andReturn();

        assertEquals(400, existing.getResponse().getStatus());
        assertEquals(400, unused.getResponse().getStatus());
        assertEquals(JsonPath.<String>read(existing.getResponse().getContentAsString(), "$.fieldErrors.password"),
                JsonPath.<String>read(unused.getResponse().getContentAsString(), "$.fieldErrors.password"));
    }

    @Test
    void aPersonalPasswordIsRefusedBeforeLookingForTheAccount_soNothingIsSent() throws Exception {
        String taken = registerVerified();
        emailSender.clear();

        register(taken, "xx" + taken, NAME).andExpect(status().isBadRequest());

        assertEquals(0, emailSender.sent().size());
    }

    // CA4

    @Test
    void aPhraseWithSpacesAndAccents_withoutUppercaseDigitOrSymbol_isAcceptedOnEveryRoute() throws Exception {
        assertAcceptedEverywhere("o céu está azul hoje");
    }

    @Test
    void aCommonWordInsideALongerPhrase_isAccepted() throws Exception {
        assertAcceptedEverywhere("minha senha123 favorita");
    }

    // CA5

    @Test
    void anOldPasswordWithEightCharacters_stillLogsIn() throws Exception {
        String email = newAddress();
        jdbcTemplate.update("""
                INSERT INTO users (email, password_hash, name, created_at, email_verified_at)
                VALUES (?, ?, ?, NOW(), NOW())
                """, email, passwordEncoder.encode("12345678"), "Conta Antiga");

        login(email, "12345678").andExpect(status().isOk());
    }

    // Rotas

    private void assertRefusedEverywhere(String password, String message) throws Exception {
        assertRefusedEverywhere(address -> password, message);
    }

    private void assertRefusedEverywhere(Function<String, String> passwordFor, String message)
            throws Exception {
        String email = newAddress();
        register(email, passwordFor.apply(email), NAME)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value(message));

        String member = registerVerified();
        changePassword(accessToken(member), PASSWORD, passwordFor.apply(member))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").value(message));
        login(member, PASSWORD).andExpect(status().isOk());

        String forgotten = registerVerified();
        forgot(forgotten).andExpect(status().isAccepted());
        String token = lastTokenSentTo(forgotten);
        reset(token, passwordFor.apply(forgotten))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").value(message));
        // O link continua valendo para outra tentativa.
        reset(token, "uma frase que serve").andExpect(status().isNoContent());
    }

    private void assertAcceptedEverywhere(String password) throws Exception {
        String email = newAddress();
        register(email, password, NAME).andExpect(status().isAccepted());
        markVerified(email);
        login(email, password).andExpect(status().isOk());

        String member = registerVerified();
        changePassword(accessToken(member), PASSWORD, password).andExpect(status().isNoContent());
        login(member, password).andExpect(status().isOk());

        String forgotten = registerVerified();
        forgot(forgotten).andExpect(status().isAccepted());
        reset(lastTokenSentTo(forgotten), password).andExpect(status().isNoContent());
        login(forgotten, password).andExpect(status().isOk());
    }

    private String registerVerified() throws Exception {
        String email = newAddress();
        register(email, PASSWORD, NAME).andExpect(status().isAccepted());
        markVerified(email);
        emailSender.clear();
        return email;
    }

    private ResultActions register(String email, String password, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(email, password, name))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, password))));
    }

    private String accessToken(String email) throws Exception {
        String body = login(email, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private ResultActions changePassword(String token, String currentPassword, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("currentPassword", currentPassword, "newPassword", newPassword))));
    }

    private ResultActions forgot(String email) throws Exception {
        int n = IP_SEQUENCE.incrementAndGet();
        String ip = "10.40." + (n / 250) + "." + (n % 250 + 1);

        return mockMvc.perform(post("/api/v1/auth/password/forgot")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email))));
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token, "newPassword", newPassword))));
    }

    private String lastTokenSentTo(String email) {
        List<EmailMessage> sent = emailSender.sent().stream()
                .filter(message -> message.to().equals(email))
                .toList();
        Matcher matcher = TOKEN.matcher(sent.get(sent.size() - 1).text());
        assertTrue(matcher.find(), "no link in the last e-mail to " + email);
        return matcher.group(1);
    }

    private String newAddress() {
        return "pol-" + UUID.randomUUID() + "@example.com";
    }
}
