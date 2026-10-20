package vn.giapha;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GiaphaApplication {
    public static void main(String[] args) {
        SpringApplication.run(GiaphaApplication.class, args);
    }
}
