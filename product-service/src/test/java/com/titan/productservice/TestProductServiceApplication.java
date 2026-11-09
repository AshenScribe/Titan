package com.titan.productservice;

import org.springframework.boot.SpringApplication;

public final class TestProductServiceApplication {

    private TestProductServiceApplication() {}

    public static void main(String[] args) {
        SpringApplication.from(ProductServiceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
