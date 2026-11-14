//------------------------------------------------------------------------------
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
//------------------------------------------------------------------------------

import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public class OrderServiceContainer extends BaseServiceContainer<OrderServiceContainer> {

    public static final int HTTP_PORT = 8080;
    private static final DockerImageName DEFAULT_IMAGE = DockerImageName.parse("titan/order-service:test");

    public OrderServiceContainer() {
        this(DEFAULT_IMAGE);
    }

    public OrderServiceContainer( DockerImageName imageName ) {
        super(imageName, "order-service");
        withExposedPorts(HTTP_PORT);
        withNetworkAliases("order-service");
        waitingFor(Wait.forHttp("/actuator/health")
                       .forPort(HTTP_PORT)
                       .forStatusCode(200)
                       .withStartupTimeout(Duration.ofSeconds(60)));
    }

    public OrderServiceContainer withPostgres( PostgreSQLContainer postgres, String orderDbName ) {
        dependsOn(postgres);
        withEnv("DB_HOST", "postgres");
        withEnv("DB_PORT", "5432");
        withEnv("DB_NAME", orderDbName);
        withEnv("DB_USERNAME", postgres.getUsername());
        withEnv("DB_PASSWORD", postgres.getPassword());
        return self();
    }

    public OrderServiceContainer withProductService( ProductServiceContainer productService ) {
        dependsOn(productService);
        withEnv("TITAN_PRODUCT_GRPC_HOST", "product-service");
        withEnv("TITAN_PRODUCT_GRPC_PORT", String.valueOf(ProductServiceContainer.GRPC_PORT));
        return self();
    }

    public String getHttpBaseUrl() {
        return "http://" + getHost() + ":" + getMappedPort(HTTP_PORT);
    }
}