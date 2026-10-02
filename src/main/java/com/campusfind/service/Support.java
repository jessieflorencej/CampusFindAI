package com.campusfind.service;

import com.campusfind.entity.*;
import com.campusfind.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.context.request.*;
import org.springframework.http.HttpStatus;
import java.security.Principal;
import java.time.Instant;

@Service
public class Support {
    private final UserRepository users;
    private final AuditRepository audits;
    private final NotificationRepository notifications;
    public Support(UserRepository users,AuditRepository audits,NotificationRepository notifications) { this.users=users; this.audits=audits; this.notifications=notifications; }
    public UserAccount user(Principal principal) {
        if (principal==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Please sign in to continue.");
        UserAccount user=users.findByEmail(principal.getName()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Please sign in again."));
        if (!user.active || (user.lockedUntil!=null && user.lockedUntil.isAfter(Instant.now()))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This account is temporarily unavailable. Contact campus administration.");
        return user;
    }
    public void admin(UserAccount user) { if (user==null || !user.active || !"ADMIN".equals(user.role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Administrator access is required."); }
    public void audit(UserAccount actor,String action,String resource) {
        AuditEvent event=new AuditEvent(); event.actorId=actor==null?null:actor.id; event.action=action; event.resource=resource;
        event.result=action.contains("FAILED")?"FAILED":"SUCCESS";
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes context) event.ip=context.getRequest().getRemoteAddr();
        else event.ip="local";
        audits.save(event);
    }
    public void notify(UserAccount user,String title,String message) { Notification n=new Notification(); n.user=user; n.title=title; n.message=message; notifications.save(n); }
}
