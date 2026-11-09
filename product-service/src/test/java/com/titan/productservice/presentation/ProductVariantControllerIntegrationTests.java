package com.titan.productservice.presentation;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.titan.productservice.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProductVariantControllerIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanDatabase() {
        jdbc.execute(
                "TRUNCATE product_media, product_variants, product_categories, products, categories, brands CASCADE");
    }

    @Test
    void createsVariantAndIncrementsProductVersion() throws Exception {
        String productId = createProduct("variant-product");

        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", productId)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"sku":"SKU-1","barcode":"BAR-1","price":1999.00,
                                         "compareAtPrice":2499.00,"currency":"INR",
                                         "attributes":{"size":"M"}}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku", equalTo("SKU-1")));

        mockMvc.perform(get("/api/v1/admin/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", equalTo(1)));
    }

    @Test
    void rejectsDuplicateSkuAndBarcode() throws Exception {
        String productId = createProduct("duplicate-product");
        createVariant(productId, "SKU-1", "BAR-1", 0);

        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", productId)
                                .header("If-Match", "\"1\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(variantJson("SKU-1", "BAR-2")))
                .andExpect(status().isConflict());
        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", productId)
                                .header("If-Match", "\"1\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(variantJson("SKU-2", "BAR-1")))
                .andExpect(status().isConflict());
    }

    @Test
    void publishesProductAndPublicApiReturnsOnlyStorefrontFields() throws Exception {
        String productId = createProduct("public-product");
        createVariant(productId, "PUBLIC-1", "PUBLIC-BAR", 0);

        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/publish", productId)
                                .header("If-Match", "\"1\""))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug", equalTo("public-product")))
                .andExpect(jsonPath("$.variants", hasSize(1)))
                .andExpect(jsonPath("$.variants[0].price", equalTo(1999.00)))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.variants[0].sku").doesNotExist());
        mockMvc.perform(get("/api/v1/products/slug/public-product")).andExpect(status().isOk());
        mockMvc.perform(
                        get("/api/v1/products")
                                .queryParam("q", "public")
                                .queryParam("minPrice", "1900")
                                .queryParam("maxPrice", "2000")
                                .queryParam("currency", "INR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void finalActiveVariantCannotBeDeactivatedForActiveProduct() throws Exception {
        String productId = createProduct("protected-product");
        String variantId = createVariant(productId, "PROTECTED-1", "PROTECTED-BAR", 0);
        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/publish", productId)
                                .header("If-Match", "\"1\""))
                .andExpect(status().isOk());

        mockMvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}/deactivate",
                                        productId,
                                        variantId)
                                .header("If-Match", "\"2\""))
                .andExpect(status().isUnprocessableEntity());
    }

    private String createProduct(String slug) throws Exception {
        return mockMvc.perform(
                        post("/api/v1/admin/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Product\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private String createVariant(String productId, String sku, String barcode, long version)
            throws Exception {
        return mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", productId)
                                .header("If-Match", "\"" + version + "\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(variantJson(sku, barcode)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private String variantJson(String sku, String barcode) {
        return "{\"sku\":\""
                + sku
                + "\",\"barcode\":\""
                + barcode
                + "\",\"price\":1999.00,\"currency\":\"INR\"}";
    }
}
