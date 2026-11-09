package com.titan.product.orderservice.config;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductGrpcClientConfiguration {
    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel productGrpcChannel(
            @Value("${titan.product.grpc.host:localhost}") String host,
            @Value("${titan.product.grpc.port:9091}") int port) {
        return NettyChannelBuilder.forAddress(host, port).usePlaintext().build();
    }
}
