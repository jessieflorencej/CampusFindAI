package com.campusfind.security;

import com.campusfind.repository.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Instant;

public class ActiveAccountFilter extends OncePerRequestFilter {
    private final UserRepository users;
    public ActiveAccountFilter(UserRepository users) { this.users=users; }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if (auth!=null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            var user=users.findByEmail(auth.getName()).orElse(null);
            Object version=req.getSession(false)==null?null:req.getSession(false).getAttribute("campus.authVersion");
            if (user==null || !user.active || (user.lockedUntil!=null && user.lockedUntil.isAfter(Instant.now())) || (version!=null&&!version.equals(user.authVersion))) {
                new SecurityContextLogoutHandler().logout(req,res,auth);
                if (req.getRequestURI().startsWith("/api/")) { res.setStatus(403); res.setContentType("application/json"); res.getWriter().write("{\"message\":\"This account is unavailable. Contact campus administration.\"}"); }
                else res.sendRedirect("/login");
                return;
            }
            if(version==null)req.getSession().setAttribute("campus.authVersion",user.authVersion);
        }
        chain.doFilter(req,res);
    }
}
