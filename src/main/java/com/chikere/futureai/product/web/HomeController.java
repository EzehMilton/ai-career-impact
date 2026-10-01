package com.chikere.futureai.product.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The product entry point and the two product-level informational pages.
 *
 * <p>These pages are static server-rendered content. They read no assessment, make no provider
 * call and hold no state.</p>
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @GetMapping("/methodology")
    public String methodology() {
        return "methodology";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }
}
