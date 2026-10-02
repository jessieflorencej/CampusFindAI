package com.campusfind.controller;

import com.campusfind.service.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;

@RestController @RequestMapping("/api")
public class AuthController {
    private final UserService users;
    private final Support support;
    private final CampusMailService mail;
    public AuthController(UserService users,Support support,CampusMailService mail){this.users=users;this.support=support;this.mail=mail;}
    @GetMapping("/auth/csrf") public Map<String,Object> csrf(CsrfToken token) {return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
    @GetMapping("/auth/me") public Map<String,Object> me(Principal principal) {Map<String,Object> result=new LinkedHashMap<>();result.put("user",principal==null?null:users.safe(support.user(principal)));result.put("mailMode",mail.mode());return result;}
    @PostMapping("/auth/register") public Map<String,String> register(@Valid @RequestBody UserService.RegisterRequest request) {users.register(request);return message("Account created. Verify your email address, then visit the campus desk for college ID verification."+outboxNotice());}
    @PostMapping("/auth/verify-email") public Map<String,String> verify(@RequestBody Map<String,String> request) {users.verifyEmail(request.get("token"));return message("Email address verified. Your college ID must also be verified by campus administration before you can claim items.");}
    @PostMapping("/auth/resend-verification") public Map<String,String> resend(Principal principal) {users.sendVerification(support.user(principal));return message("A verification link has been prepared if your email still needs verification."+outboxNotice());}
    @PostMapping("/auth/forgot-password") public Map<String,String> forgot(@RequestBody Map<String,String> request) {users.forgotPassword(request.get("email"));return message("If an active account matches that email, a password reset link has been prepared."+outboxNotice());}
    @PostMapping("/auth/reset-password") public Map<String,String> reset(@RequestBody Map<String,String> request,HttpServletRequest http,HttpServletResponse response) {users.resetPassword(request.get("token"),request.get("password"));logout(http,response);return message("Password reset. Sign in with your new password.");}
    @GetMapping("/profile") public Map<String,Object> profile(Principal principal) {return users.safe(support.user(principal));}
    @PutMapping("/profile") public Map<String,Object> update(Principal principal,@Valid @RequestBody UserService.ProfileRequest request) {return users.profile(support.user(principal),request);}
    @PostMapping("/profile/password") public Map<String,String> password(Principal principal,@RequestBody Map<String,String> request,HttpServletRequest http,HttpServletResponse response) {users.changePassword(support.user(principal),request.get("currentPassword"),request.get("newPassword"));logout(http,response);return message("Password changed. Sign in again with your new password.");}
    private void logout(HttpServletRequest request,HttpServletResponse response) {new SecurityContextLogoutHandler().logout(request,response,SecurityContextHolder.getContext().getAuthentication());}
    private String outboxNotice(){return "outbox".equalsIgnoreCase(mail.mode())?" Local demo: open the newest email file in .local/mail on this laptop. No email is sent externally.":"";}
    private Map<String,String> message(String text){return Map.of("message",text);}
}
