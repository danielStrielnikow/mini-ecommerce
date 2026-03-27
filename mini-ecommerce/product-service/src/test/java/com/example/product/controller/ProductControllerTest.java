package com.example.product.controller;

import com.example.product.dto.response.ProductResponse;
import com.example.product.entity.ProductStatus;
import com.example.product.exception.ProductNotFoundException;
import com.example.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean  private ProductService productService;

    private final UUID productId = UUID.randomUUID();

    private ProductResponse sampleResponse() {
        return new ProductResponse(productId, "Laptop Pro", "High-end laptop",
                new BigDecimal("4999.99"), ProductStatus.ACTIVE, Instant.now());
    }

    // ── GET /api/products ────────────────────────────────────────────────────

    @Test
    void getAll_shouldReturn200WithPagedResults() throws Exception {
        Page<ProductResponse> page = new PageImpl<>(List.of(sampleResponse()));
        given(productService.findAll(any(), any(Pageable.class))).willReturn(page);

        mockMvc.perform(get("/api/products").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Laptop Pro"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getAll_withFilters_shouldReturn200() throws Exception {
        Page<ProductResponse> page = new PageImpl<>(List.of(sampleResponse()));
        given(productService.findAll(any(), any(Pageable.class))).willReturn(page);

        mockMvc.perform(get("/api/products")
                        .param("name", "laptop")
                        .param("minPrice", "100")
                        .param("maxPrice", "9999")
                        .param("status", "ACTIVE")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void getAll_whenEmpty_shouldReturn200WithEmptyPage() throws Exception {
        given(productService.findAll(any(), any(Pageable.class))).willReturn(Page.empty());

        mockMvc.perform(get("/api/products").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // ── GET /api/products/{id} ───────────────────────────────────────────────

    @Test
    void getById_whenExists_shouldReturn200() throws Exception {
        given(productService.findById(productId)).willReturn(sampleResponse());

        mockMvc.perform(get("/api/products/{id}", productId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId.toString()))
                .andExpect(jsonPath("$.name").value("Laptop Pro"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getById_whenNotFound_shouldReturn404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        given(productService.findById(unknownId)).willThrow(new ProductNotFoundException(unknownId));

        mockMvc.perform(get("/api/products/{id}", unknownId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/products ───────────────────────────────────────────────────

    @Test
    void create_shouldReturn201WithCreatedProduct() throws Exception {
        given(productService.create(any())).willReturn(sampleResponse());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Laptop Pro","description":"High-end laptop","price":4999.99}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Laptop Pro"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void create_withBlankName_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","description":"desc","price":99.99}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withNegativePrice_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Laptop","description":"desc","price":-1}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /api/products/{id} ───────────────────────────────────────────────

    @Test
    void update_shouldReturn200WithUpdatedProduct() throws Exception {
        given(productService.update(eq(productId), any())).willReturn(sampleResponse());

        mockMvc.perform(put("/api/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated Laptop","description":"Updated desc","price":5999.99,"status":"INACTIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Laptop Pro"));
    }

    @Test
    void update_whenNotFound_shouldReturn404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        given(productService.update(eq(unknownId), any()))
                .willThrow(new ProductNotFoundException(unknownId));

        mockMvc.perform(put("/api/products/{id}", unknownId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated","description":"desc","price":100.00,"status":"ACTIVE"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_withBlankName_shouldReturn400() throws Exception {
        mockMvc.perform(put("/api/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","description":"desc","price":100.00,"status":"ACTIVE"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /api/products/{id}/deactivate & /activate ─────────────────────

    @Test
    void deactivate_shouldReturn200WithInactiveProduct() throws Exception {
        ProductResponse inactive = new ProductResponse(productId, "Laptop Pro", "desc",
                new BigDecimal("4999.99"), ProductStatus.INACTIVE, Instant.now());
        given(productService.deactivate(productId)).willReturn(inactive);

        mockMvc.perform(patch("/api/products/{id}/deactivate", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void deactivate_whenNotFound_shouldReturn404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        given(productService.deactivate(unknownId)).willThrow(new ProductNotFoundException(unknownId));

        mockMvc.perform(patch("/api/products/{id}/deactivate", unknownId))
                .andExpect(status().isNotFound());
    }

    @Test
    void activate_shouldReturn200WithActiveProduct() throws Exception {
        given(productService.activate(productId)).willReturn(sampleResponse());

        mockMvc.perform(patch("/api/products/{id}/activate", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void activate_whenNotFound_shouldReturn404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        given(productService.activate(unknownId)).willThrow(new ProductNotFoundException(unknownId));

        mockMvc.perform(patch("/api/products/{id}/activate", unknownId))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/products/{id} ────────────────────────────────────────────

    @Test
    void delete_shouldReturn204() throws Exception {
        willDoNothing().given(productService).delete(productId);

        mockMvc.perform(delete("/api/products/{id}", productId))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_whenNotFound_shouldReturn404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        willThrow(new ProductNotFoundException(unknownId)).given(productService).delete(unknownId);

        mockMvc.perform(delete("/api/products/{id}", unknownId))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/products/{id}/permanent ──────────────────────────────────

    @Test
    void hardDelete_shouldReturn204() throws Exception {
        willDoNothing().given(productService).hardDelete(productId);

        mockMvc.perform(delete("/api/products/{id}/permanent", productId))
                .andExpect(status().isNoContent());
    }

    @Test
    void hardDelete_whenNotFound_shouldReturn404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        willThrow(new ProductNotFoundException(unknownId)).given(productService).hardDelete(unknownId);

        mockMvc.perform(delete("/api/products/{id}/permanent", unknownId))
                .andExpect(status().isNotFound());
    }
}
