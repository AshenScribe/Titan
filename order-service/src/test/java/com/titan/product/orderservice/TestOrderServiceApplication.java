package com.titan.product.orderservice;

import org.springframework.boot.SpringApplication;

public final class TestOrderServiceApplication {

    private TestOrderServiceApplication() {}

    public static void main(String[] args) {
        SpringApplication.from(OrderServiceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
