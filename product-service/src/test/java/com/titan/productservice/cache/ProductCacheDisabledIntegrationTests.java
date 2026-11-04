package com.titan.productservice.service;
import com.titan.productservice.controller.*;
import com.titan.productservice.service.*;
import com.titan.productservice.dto.*;
import com.titan.productservice.exception.*;
import com.titan.productservice.config.*;
import com.titan.productservice.entity.*;
import com.titan.productservice.repository.*;
import com.titan.productservice.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.titan.productservice.TestcontainersConfiguration;
import com.titan.productservice.repository.AbstractRepositoryIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"titan.cache.l2-enabled=false", "titan.cache.l1-enabled=false"})
class ProductCacheDisabledIntegrationTests extends AbstractRepositoryIntegrationTest {
    @Autowired private ProductCacheService cache;

    @Test
    void disabledL2DoesNotCountMisses() {
        double missesBefore = cache.l2Misses();

        assertTrue(cache.findActiveById(UUID.randomUUID()).isEmpty());

        assertEquals(missesBefore, cache.l2Misses());
    }
}
