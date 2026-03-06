package ru.coproducts.moysklad.reco;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MoySkladRecoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoySkladRecoApplication.class, args);
    }
}

