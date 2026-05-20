package nus.edu.u.user;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test k8ss
 *
 * @author Lu Shuwen
 * @date 2025-10-16
 */
@SpringBootApplication
@EnableDubbo
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
