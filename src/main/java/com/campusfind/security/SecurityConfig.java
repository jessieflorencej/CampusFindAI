package com.campusfind.security;

import com.campusfind.repository.UserRepository;
import com.campusfind.service.CryptoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;
import org.springframework.security.web.csrf.*;
import java.time.Instant;
import java.util.Map;

@Configuration
public class SecurityConfig {
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean public UserDetailsService userDetailsService(UserRepository users) {
        return email->{ var u=users.findByEmail(email).orElseThrow(()->new UsernameNotFoundException("Account unavailable")); return User.withUsername(u.email).password(u.passwordHash).roles(u.role).disabled(!u.active).accountLocked(u.lockedUntil!=null&&u.lockedUntil.isAfter(Instant.now())).build(); };
    }
    @Bean public SecurityFilterChain security(HttpSecurity http,CampusAuthenticationProvider provider,UserDetailsService details,CryptoService crypto,UserRepository users,ObjectMapper json) throws Exception {
        http.authorizeHttpRequests(auth->auth
                .requestMatchers("/","/login","/admin/login","/register","/forgot-password","/reset-password","/verify-email","/error","/css/**","/js/**","/images/**","/favicon.ico").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/catalog","/api/stats","/api/items","/api/items/**","/api/uploads/**","/items","/items/**").permitAll()
                .requestMatchers("/api/auth/**","/api/public/**").permitAll()
                .requestMatchers("/admin","/admin/**","/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .csrf(csrf->csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository()).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .authenticationProvider(provider)
            .formLogin(form->form.loginPage("/login").loginProcessingUrl("/api/auth/login")
                .successHandler((req,res,auth)->{ users.findByEmail(auth.getName()).ifPresent(u->req.getSession().setAttribute("campus.authVersion",u.authVersion)); res.setContentType("application/json"); json.writeValue(res.getWriter(),Map.of("message","Welcome back.","redirect",auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"))?"/admin":"/dashboard")); })
                .failureHandler((req,res,error)->{ res.setStatus(401); res.setContentType("application/json"); json.writeValue(res.getWriter(),Map.of("message",error.getMessage())); }))
            .rememberMe(remember->remember.key(crypto.hash("campusfind-remember-me")).tokenValiditySeconds(604800).userDetailsService(details).rememberMeParameter("remember-me"))
            .logout(logout->logout.logoutUrl("/api/auth/logout").invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID","remember-me")
                .logoutSuccessHandler((req,res,auth)->{ res.setContentType("application/json"); res.getWriter().write("{\"message\":\"You have signed out.\"}"); }))
            .sessionManagement(session->session.sessionFixation(fixation->fixation.migrateSession()))
            .requestCache(cache->cache.disable())
            .headers(headers->headers.contentSecurityPolicy(csp->csp.policyDirectives("default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; script-src 'self'; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"))
                .referrerPolicy(policy->policy.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
            .exceptionHandling(errors->errors.authenticationEntryPoint((req,res,error)->{ if(req.getRequestURI().startsWith("/api/")){res.setStatus(401);res.setContentType("application/json");res.getWriter().write("{\"message\":\"Please sign in to continue.\"}");}else res.sendRedirect("/login"); })
                .accessDeniedHandler((req,res,error)->{res.setStatus(403);res.setContentType("application/json");res.getWriter().write("{\"message\":\"Access denied or session expired. Refresh the page and try again.\"}");}))
            .addFilterBefore(new RateLimitFilter(),UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new ActiveAccountFilter(users),RememberMeAuthenticationFilter.class);
        return http.build();
    }
}
