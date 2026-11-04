package com.titan.productservice.config;
import com.titan.productservice.controller.*;
import com.titan.productservice.service.*;
import com.titan.productservice.dto.*;
import com.titan.productservice.exception.*;
import com.titan.productservice.config.*;
import com.titan.productservice.entity.*;
import com.titan.productservice.repository.*;
import com.titan.productservice.util.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProductCacheConfigurationTests {
    @Test
    void configuresCacheWithOptionalExpirationPolicies() {
        ProductCacheConfiguration configuration = new ProductCacheConfiguration();
        ProductCacheProperties properties = new ProductCacheProperties();
        properties.setL1Ttl(Duration.ZERO);
        properties.setL1Tti(Duration.ZERO);
        assertNotNull(configuration.productL1Cache(properties));

        properties.setL1Ttl(Duration.ofSeconds(-1));
        properties.setL1Tti(Duration.ofSeconds(-1));
        assertNotNull(configuration.productL1Cache(properties));

        properties.setL1Tti(Duration.ofSeconds(1));
        properties.setL1Ttl(Duration.ofSeconds(1));
        assertNotNull(configuration.productL1Cache(properties));
    }
}
