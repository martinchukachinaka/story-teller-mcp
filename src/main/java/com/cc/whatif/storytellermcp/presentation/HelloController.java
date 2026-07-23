package com.cc.whatif.storytellermcp.presentation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequestMapping("hello")
@RestController
public class HelloController {

	@GetMapping
	public String sayHello(@RequestParam(required = false, defaultValue = "Stranger") String name) {
		log.info("Saying hello to {}", name);
		return "Hello " + name;
	}
}
