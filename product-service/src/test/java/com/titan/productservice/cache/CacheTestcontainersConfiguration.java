package com.titan.productservice;
import com.titan.productservice.controller.*;
import com.titan.productservice.service.*;
import com.titan.productservice.dto.*;
import com.titan.productservice.exception.*;
import com.titan.productservice.config.*;
import com.titan.productservice.entity.*;
import com.titan.productservice.repository.*;
import com.titan.productservice.util.*;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class CacheTestcontainersConfiguration {
    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                .withExposedPorts(6379);
    }
}
