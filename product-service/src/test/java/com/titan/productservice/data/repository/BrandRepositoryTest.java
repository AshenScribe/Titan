// ------------------------------------------------------------------------------
// MIT License
//
// Copyright (c) 2026 Rishabh Saraswat
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
// ------------------------------------------------------------------------------

package com.titan.productservice.data.repository;

import com.titan.productservice.TestcontainersConfiguration;
import com.titan.productservice.entity.BrandEntity;
import com.titan.productservice.entity.ProductEntity;
import com.titan.productservice.repository.BrandRepository;
import com.titan.productservice.repository.ProductRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@JdbcTest

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, BrandRepository.class, ProductRepository.class})
class BrandRepositoryTest {

    @Autowired
    private BrandRepository brands;
    @Autowired
    ProductRepository products;

    @Test
    void insertFindUpdateActivePaginationAndDelete() {
        BrandEntity first = brand("first");
        BrandEntity second = brand("second");
        brands.insert(first);
        brands.insert(second);
        Assertions.assertThat(brands.findById(first.getId())).get().extracting(BrandEntity::getId).isEqualTo(first.getId());
        Assertions.assertThat(brands.findBySlug("FIRST")).get().extracting(BrandEntity::getId).isEqualTo(first.getId());
        Assertions.assertThat(brands.findActive(1, 0)).hasSize(1);
        first.setName("Updated");
        Assertions.assertThat(brands.update(first)).isEqualTo(1);
        Assertions.assertThat(brands.findById(first.getId())).get().extracting(BrandEntity::getName).isEqualTo("Updated");
        Assertions.assertThat(brands.deleteById(first.getId())).isEqualTo(1);
        Assertions.assertThat(brands.findById(first.getId())).isEmpty();
        Assertions.assertThat(brands.findById(UUID.randomUUID())).isEmpty();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void duplicateSlugAndBlankNameAreRejected() {
        brands.insert(brand("same"));
        BrandEntity duplicate = brand("SAME");
        Assertions.assertThatThrownBy(() -> brands.insert(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
        BrandEntity blank = brand("blank");
        blank.setName(" ");
        Assertions.assertThatThrownBy(() -> brands.insert(blank)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void jsonObjectAndNullOptionalFieldsRoundTrip() {
        BrandEntity value = brand("json");
        value.setDescription(null);
        value.setMetadata(Map.of("array", List.of(1, 2), "nested", Map.of("ok", true)));
        brands.insert(value);
        Assertions.assertThat(brands.findById(value.getId())).get().extracting(BrandEntity::getMetadata).isEqualTo(value.getMetadata());
    }


    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void deletingBrandReferencedByProductIsRestricted() {
        BrandEntity value = brand("referenced");
        brands.insert(value);
        var product = product("brand-reference");
        product.setBrandId(value.getId());
        products.insert(product);
        Assertions.assertThatThrownBy(() -> brands.deleteById(value.getId())).isInstanceOf(DataIntegrityViolationException.class);
        Assertions.assertThat(brands.findById(value.getId())).isPresent();
    }

    @Test
    void batchInsertSupportsEmptyAndMultipleInputs() {
        Assertions.assertThat(brands.insertBatch(List.of())).isEmpty();
        Assertions.assertThat(brands.insertBatch(null)).isEmpty();
        Assertions.assertThat(brands.insertBatch(List.of(brand("a"), brand("b")))).hasSize(2);
    }

    @Test
    void paginationRejectsInvalidBounds() {
        Assertions.assertThatThrownBy(() -> brands.findActive(0, 0)).isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> brands.findActive(10, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    private BrandEntity brand(String slug) {
        return new BrandEntity(UUID.randomUUID(), "Brand " + slug, slug, null, null, null, Map.of("nested", Map.of("enabled", true)), true, null, null);
    }

    private ProductEntity product(String slug) {
        return new ProductEntity(UUID.randomUUID(), null, "Product " + slug, slug, null, "DRAFT", Map.of("nested", Map.of("value", 1)), 0, null, null, null, null);
    }
}
