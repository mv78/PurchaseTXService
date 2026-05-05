package com.mveksler.purchasetx.purchase.api;

import com.mveksler.purchasetx.error.exceptions.PurchaseNotFoundException;
import com.mveksler.purchasetx.purchase.domain.Purchase;
import com.mveksler.purchasetx.purchase.service.PurchaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(PurchaseController.class)
@ActiveProfiles("test")
class PurchaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PurchaseService service;

    @Test
    void createReturns201WithLocationHeader() throws Exception {
        var purchase = new Purchase("Office supplies", LocalDate.of(2024, 8, 15),
                new BigDecimal("123.45"), Instant.parse("2024-08-15T12:00:00Z"));
        when(service.create(any())).thenReturn(purchase);

        String requestBody = """
                {
                  "description": "Office supplies",
                  "transactionDate": "2024-08-15",
                  "purchaseAmountUsd": 123.45
                }
                """;

        mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.description").value("Office supplies"))
                .andExpect(jsonPath("$.purchaseAmountUsd").value(123.45));
    }

    @Test
    void createRejectsDescriptionLongerThanFiftyCharacters() throws Exception {
        String requestBody = """
                {
                  "description": "%s",
                  "transactionDate": "2024-08-15",
                  "purchaseAmountUsd": 123.45
                }
                """.formatted("a".repeat(51));

        mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.description").exists());
    }

    @Test
    void createRejectsFutureTransactionDate() throws Exception {
        String requestBody = """
                {
                  "description": "Test",
                  "transactionDate": "%s",
                  "purchaseAmountUsd": 10.00
                }
                """.formatted(LocalDate.now().plusDays(1));

        mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.transactionDate").exists());
    }

    @Test
    void createRejectsZeroAmount() throws Exception {
        String requestBody = """
                {
                  "description": "Test",
                  "transactionDate": "2024-08-15",
                  "purchaseAmountUsd": 0.00
                }
                """;

        mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.purchaseAmountUsd").exists());
    }

    @Test
    void createRejectsAmountWithMoreThanTwoDecimals() throws Exception {
        String requestBody = """
                {
                  "description": "Test",
                  "transactionDate": "2024-08-15",
                  "purchaseAmountUsd": 10.123
                }
                """;

        mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.purchaseAmountUsd").exists());
    }

    @Test
    void getByIdReturnsPurchase() throws Exception {
        var id = UUID.randomUUID();
        var purchase = new Purchase("Office supplies", LocalDate.of(2024, 8, 15),
                new BigDecimal("123.45"), Instant.now());
        when(service.findById(id)).thenReturn(purchase);

        mockMvc.perform(get("/api/v1/purchases/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Office supplies"));
    }

    @Test
    void getByIdReturns404WhenNotFound() throws Exception {
        var id = UUID.randomUUID();
        when(service.findById(id)).thenThrow(new PurchaseNotFoundException(id));

        mockMvc.perform(get("/api/v1/purchases/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Purchase not found"));
    }

    @Test
    void getByIdReturns400ForInvalidUuid() throws Exception {
        mockMvc.perform(get("/api/v1/purchases/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid parameter"))
                .andExpect(jsonPath("$.rejectedValue").value("not-a-uuid"));
    }

    @Test
    void rejectsXmlContentType() throws Exception {
        String xmlBody = """
                <purchase>
                  <description>Test</description>
                </purchase>
                """;

        mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(xmlBody))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("Unsupported media type"))
                .andExpect(jsonPath("$.supportedMediaTypes").isArray());
    }

    @Test
    void rejectsMissingContentType() throws Exception {
        String body = """
                {"description":"Test","transactionDate":"2024-08-15","purchaseAmountUsd":10.00}
                """;

        mockMvc.perform(post("/api/v1/purchases").content(body))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void rejectsUSStyleDate() throws Exception {
        String requestBody = """
                {
                  "description": "Test",
                  "transactionDate": "08/15/2024",
                  "purchaseAmountUsd": 10.00
                }
                """;

        mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void rejectsImpossibleDate() throws Exception {
        String requestBody = """
                {
                  "description": "Test",
                  "transactionDate": "2024-13-45",
                  "purchaseAmountUsd": 10.00
                }
                """;

        mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void rejectsDateWithTimeComponent() throws Exception {
        String requestBody = """
                {
                  "description": "Test",
                  "transactionDate": "2024-08-15T12:00:00",
                  "purchaseAmountUsd": 10.00
                }
                """;

        mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsValidIsoDate() throws Exception {
        var purchase = new Purchase("Test", LocalDate.of(2024, 8, 15),
                new BigDecimal("10.00"), Instant.now());
        when(service.create(any())).thenReturn(purchase);

        String requestBody = """
                {
                  "description": "Test",
                  "transactionDate": "2024-08-15",
                  "purchaseAmountUsd": 10.00
                }
                """;

        mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());
    }
}