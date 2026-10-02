package com.campusfind.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/** Per-process limits supplement persistent per-account lockout and claim attempt limits. */
public class RateLimitFilter extends OncePerRequestFilter {
    private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
    private record Window(long start,int count) {}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String path=req.getRequestURI();
        if (!path.startsWith("/api/") || "GET".equals(req.getMethod())) { chain.doFilter(req,res); return; }
        long now=System.currentTimeMillis();
        boolean auth=path.startsWith("/api/auth/");
        int limit=auth?30:120; long span=auth?600000:60000;
        if (windows.size()>10000) windows.entrySet().removeIf(e->now-e.getValue().start>600000);
        String key=req.getRemoteAddr()+":"+(auth?"auth":"write");
        Window window=windows.compute(key,(ignored,old)->old==null||now-old.start>span?new Window(now,1):new Window(old.start,old.count+1));
        if (window.count>limit) { res.setStatus(429); res.setHeader("Retry-After",String.valueOf(Math.max(1,(span-(now-window.start))/1000))); res.setContentType("application/json"); res.getWriter().write("{\"message\":\"Too many requests. Please wait and try again.\"}"); return; }
        chain.doFilter(req,res);
    }
}
