package ru.innopolis.tbank.thealth;

import org.springframework.boot.SpringApplication;

public class TestTBankHealthApplication {

    public static void main(String[] args) {
        SpringApplication.from(TBankHealthApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
