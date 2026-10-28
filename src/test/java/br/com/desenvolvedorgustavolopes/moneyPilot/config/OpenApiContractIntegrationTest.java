package br.com.desenvolvedorgustavolopes.moneyPilot.config;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 011, CA1: o contrato lido por máquina é completo. O teste falha quando uma rota nova vem sem
 * documentação, então também impede o contrato de desatualizar.
 */
class OpenApiContractIntegrationTest extends AbstractIntegrationTest {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "delete", "patch");
    private static final List<String> ENUMS = List.of("AccountType", "CategoryType", "TransactionType",
            "BillType", "BillStatus", "AlertKind", "SupportCategory");
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
            "/api/v1/auth/google", "/api/v1/auth/google/config", "/api/v1/auth/email/confirm",
            "/api/v1/auth/verification/confirm", "/api/v1/auth/verification/resend",
            "/api/v1/auth/password/forgot", "/api/v1/auth/password/reset", "/api/v1/support/tickets");

    @Autowired
    private MockMvc mockMvc;

    private JsonNode docs;

    @BeforeEach
    void loadContract() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        docs = new ObjectMapper().readTree(json);
    }

    @Test
    void everyOperation_hasSummary_description_andErrorResponses() {
        List<String> problems = new ArrayList<>();
        forEachOperation((path, method, operation) -> {
            String id = method.toUpperCase() + " " + path;
            if (operation.path("summary").asText().isBlank()) {
                problems.add(id + ": sem summary");
            }
            if (operation.path("description").asText().isBlank()) {
                problems.add(id + ": sem description");
            }
            boolean hasInput = operation.has("parameters") || operation.has("requestBody");
            boolean isPublic = operation.has("security") && operation.get("security").isEmpty();
            boolean hasError = false;
            Iterator<String> codes = operation.path("responses").fieldNames();
            while (codes.hasNext()) {
                String code = codes.next();
                hasError |= code.startsWith("4") || code.startsWith("5");
            }
            // Uma rota pública, sem entrada, não tem como errar por culpa de quem chama.
            if (!hasError && (hasInput || !isPublic)) {
                problems.add(id + ": sem resposta de erro");
            }
        });
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyDtoProperty_hasDescriptionAndExample_orIsAnEnumOrReference() {
        List<String> problems = new ArrayList<>();
        JsonNode schemas = docs.path("components").path("schemas");
        schemas.fields().forEachRemaining(entry -> {
            String name = entry.getKey();
            if (name.startsWith("Page") || !(name.endsWith("Request") || name.endsWith("Response"))) {
                return;
            }
            entry.getValue().path("properties").fields().forEachRemaining(prop -> {
                JsonNode p = prop.getValue();
                String id = name + "." + prop.getKey();
                boolean structural = p.has("$ref") || p.has("enum") || p.has("items") || p.has("additionalProperties")
                        || p.has("allOf") || p.has("oneOf");
                if (!structural && !p.has("example")) {
                    problems.add(id + ": sem example");
                }
                if (!p.has("$ref") && !p.has("allOf") && !p.has("oneOf") && p.path("description").asText().isBlank()) {
                    problems.add(id + ": sem description");
                }
            });
        });
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void successResponses_keepTheirSchema_andRequestsKeepExamples() {
        JsonNode ok = docs.path("paths").path("/api/v1/accounts/{id}").path("get").path("responses").path("200");
        assertTrue(ok.path("content").toString().contains("AccountResponse"), ok.toString());

        JsonNode login = docs.path("paths").path("/api/v1/auth/login").path("post");
        assertTrue(login.path("requestBody").toString().contains("LoginRequest"), login.toString());
        assertTrue(login.path("responses").path("200").path("content").toString().contains("AuthResponse"));
        assertEquals("true", docs.path("components").path("schemas").path("LoginRequest")
                .path("properties").path("refreshTokenInBody").path("example").asText());
    }

    @Test
    void enums_areClosedComponents() {
        JsonNode schemas = docs.path("components").path("schemas");
        for (String name : ENUMS) {
            JsonNode schema = schemas.path(name);
            assertTrue(schema.has("enum") && schema.get("enum").size() > 0, name + " deve ser componente com valores fechados");
        }
    }

    @Test
    void filterEnums_usedOnlyAsQueryParameters_areClosedValuesInline() {
        // Só aparecem como parâmetro de query, então o springdoc não cria componente para eles.
        assertEquals(Set.of("INCOME", "EXPENSE", "TRANSFER"), Set.copyOf(queryEnum("/api/v1/transactions", "type")));
        assertTrue(queryEnum("/api/v1/bills", "status").size() > 1);
    }

    private List<String> queryEnum(String path, String parameter) {
        List<String> values = new ArrayList<>();
        for (JsonNode p : docs.path("paths").path(path).path("get").path("parameters")) {
            if (parameter.equals(p.path("name").asText())) {
                p.path("schema").path("enum").forEach(v -> values.add(v.asText()));
            }
        }
        return values;
    }

    @Test
    void bearerAuth_isDeclared_andOnlyThePublicRoutesOptOut() {
        JsonNode scheme = docs.path("components").path("securitySchemes").path(OpenApiConfig.BEARER);
        assertEquals("http", scheme.path("type").asText());
        assertEquals("bearer", scheme.path("scheme").asText());

        List<String> problems = new ArrayList<>();
        forEachOperation((path, method, operation) -> {
            boolean optedOut = operation.has("security") && operation.get("security").isEmpty();
            boolean shouldBePublic = PUBLIC_PATHS.contains(path);
            if (optedOut != shouldBePublic) {
                problems.add(method.toUpperCase() + " " + path + (shouldBePublic ? ": deveria ser pública" : ": não deveria ser pública"));
            }
        });
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        assertTrue(docs.path("security").toString().contains(OpenApiConfig.BEARER));
    }

    private void forEachOperation(OperationVisitor visitor) {
        docs.path("paths").fields().forEachRemaining(pathEntry -> pathEntry.getValue().fields().forEachRemaining(m -> {
            if (HTTP_METHODS.contains(m.getKey())) {
                visitor.visit(pathEntry.getKey(), m.getKey(), m.getValue());
            }
        }));
    }

    @FunctionalInterface
    private interface OperationVisitor {
        void visit(String path, String method, JsonNode operation);
    }
}
