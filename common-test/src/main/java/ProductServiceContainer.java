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

import com.redis.testcontainers.RedisContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public class ProductServiceContainer extends BaseServiceContainer<ProductServiceContainer> {

    public static final int HTTP_PORT = 8080;
    public static final int GRPC_PORT = 9091;
    private static final DockerImageName DEFAULT_IMAGE = DockerImageName.parse("titan/product-service:test");

    public ProductServiceContainer() {
        this(DEFAULT_IMAGE);
    }

    public ProductServiceContainer( DockerImageName imageName ) {
        super(imageName, "product-service");
        withExposedPorts(HTTP_PORT, GRPC_PORT);
        withNetworkAliases("product-service");
        waitingFor(Wait.forHttp("/actuator/health")
                       .forPort(HTTP_PORT)
                       .forStatusCode(200)
                       .withStartupTimeout(Duration.ofSeconds(60)));
    }

    public ProductServiceContainer withPostgres( PostgreSQLContainer postgres ) {
        dependsOn(postgres);
        withEnv("DB_HOST", "postgres");
        withEnv("DB_PORT", "5432");
        withEnv("DB_NAME", postgres.getDatabaseName());
        withEnv("DB_USERNAME", postgres.getUsername());
        withEnv("DB_PASSWORD", postgres.getPassword());
        return self();
    }

    public ProductServiceContainer withRedis( RedisContainer redis ) {
        dependsOn(redis);
        withEnv("SPRING_DATA_REDIS_HOST", "redis");
        withEnv("SPRING_DATA_REDIS_PORT", "6379");
        withEnv("TITAN_CACHE_L2_ENABLED", "true");
        return self();
    }

    public String getHttpBaseUrl() {
        return "http://" + getHost() + ":" + getMappedPort(HTTP_PORT);
    }

    public int getGrpcMappedPort() {
        return getMappedPort(GRPC_PORT);
    }
}