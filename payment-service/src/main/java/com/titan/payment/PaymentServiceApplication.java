package com.titan.payment;

import com.titan.payment.config.KafkaAppProperties;
import com.titan.payment.config.RazorpayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ RazorpayProperties.class, KafkaAppProperties.class })
public class PaymentServiceApplication {

    public static void main( String[] args ) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

}
