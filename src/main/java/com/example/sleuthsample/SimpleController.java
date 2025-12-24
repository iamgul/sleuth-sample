
package com.example.sleuthsample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimpleController {

    private static final Logger log = LoggerFactory.getLogger(SimpleController.class);

    @GetMapping("/hello")
    public String hello() {
        log.info("Hello from Sleuth sample");
        return "hello";
    }
}
