package com.titan.product.orderservice;

import com.titan.product.orderservice.config.OrderCacheProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(OrderCacheProperties.class)
public class OrderServiceApplication {

    private OrderServiceApplication() {}

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
