package com.campusfind.controller;
import com.campusfind.ai.CampusFindAssistant;
import com.campusfind.service.Support;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.security.Principal;
@RestController
public class AssistantController {
    private final CampusFindAssistant assistant;private final Support support;
    public AssistantController(CampusFindAssistant assistant,Support support){this.assistant=assistant;this.support=support;}
    @PostMapping("/api/assistant") public Object chat(@RequestBody Map<String,String> input,HttpSession session,Principal p){return assistant.answer(input.get("message"),session,support.user(p));}
}
