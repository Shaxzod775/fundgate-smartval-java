package uz.fundgate.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "uz.fundgate")
public class SmartValGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartValGatewayApplication.class, args);
    }
}
