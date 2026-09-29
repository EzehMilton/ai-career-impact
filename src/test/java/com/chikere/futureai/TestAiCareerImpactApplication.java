package com.chikere.futureai;

import org.springframework.boot.SpringApplication;

public class TestAiCareerImpactApplication {

	public static void main(String[] args) {
		SpringApplication.from(AiCareerImpactApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
