package com.titan.productservice;

import com.titan.productservice.config.*;
import com.titan.productservice.controller.*;
import com.titan.productservice.dto.*;
import com.titan.productservice.entity.*;
import com.titan.productservice.exception.*;
import com.titan.productservice.repository.*;
import com.titan.productservice.service.*;
import com.titan.productservice.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProductServiceApplicationTests {

    @Test
    void contextLoads() {}
}
