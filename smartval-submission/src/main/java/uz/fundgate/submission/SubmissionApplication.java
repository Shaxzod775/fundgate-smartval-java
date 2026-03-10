package uz.fundgate.submission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "uz.fundgate")
@EnableAsync
public class SubmissionApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubmissionApplication.class, args);
    }
}
