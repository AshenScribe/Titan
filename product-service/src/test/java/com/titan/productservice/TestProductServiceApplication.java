package com.titan.productservice;
import com.titan.productservice.controller.*;
import com.titan.productservice.service.*;
import com.titan.productservice.dto.*;
import com.titan.productservice.exception.*;
import com.titan.productservice.config.*;
import com.titan.productservice.entity.*;
import com.titan.productservice.repository.*;
import com.titan.productservice.util.*;
import com.titan.productservice.controller.*;
import com.titan.productservice.service.*;
import com.titan.productservice.dto.*;
import com.titan.productservice.exception.*;
import com.titan.productservice.config.*;
import com.titan.productservice.entity.*;
import com.titan.productservice.repository.*;
import com.titan.productservice.util.*;

import org.springframework.boot.SpringApplication;

public final class TestProductServiceApplication {

    private TestProductServiceApplication() {}

    public static void main(String[] args) {
        SpringApplication.from(ProductServiceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
