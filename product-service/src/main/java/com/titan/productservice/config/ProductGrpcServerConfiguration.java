package com.titan.productservice.config;

import com.titan.productservice.presentation.controller.ProductCatalogGrpcService;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductGrpcServerConfiguration {
    private final Server server;

    public ProductGrpcServerConfiguration(
            ProductCatalogGrpcService service, @Value("${titan.grpc.port:9091}") int port)
            throws IOException {
        server = NettyServerBuilder.forPort(port).addService(service).build().start();
    }

    @Bean
    Server productGrpcServer() {
        return server;
    }

    @PreDestroy
    void stop() {
        server.shutdown();
    }
}
