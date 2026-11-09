package com.titan.productservice;

import com.titan.productservice.config.*;
import com.titan.productservice.controller.*;
import com.titan.productservice.dto.*;
import com.titan.productservice.entity.*;
import com.titan.productservice.exception.*;
import com.titan.productservice.repository.*;
import com.titan.productservice.service.*;
import com.titan.productservice.util.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    }
}
