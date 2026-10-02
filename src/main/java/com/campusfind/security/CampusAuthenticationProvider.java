package com.campusfind.security;

import com.campusfind.entity.UserAccount;
import com.campusfind.repository.UserRepository;
import com.campusfind.service.Support;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.*;
import java.time.Instant;
import java.util.*;

@Component
public class CampusAuthenticationProvider implements AuthenticationProvider {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final Support support;
    private final String dummyHash;
    public CampusAuthenticationProvider(UserRepository users,PasswordEncoder passwords,Support support) { this.users=users; this.passwords=passwords; this.support=support; this.dummyHash=passwords.encode(UUID.randomUUID().toString()); }
    @Override @Transactional(noRollbackFor=AuthenticationException.class)
    public Authentication authenticate(Authentication input) throws AuthenticationException {
        String email=input.getName().trim().toLowerCase(Locale.ROOT);
        UserAccount candidate=users.findByEmail(email).orElse(null);
        if (candidate==null) { passwords.matches(String.valueOf(input.getCredentials()),dummyHash); throw new BadCredentialsException("Email or password is incorrect."); }
        UserAccount user=users.findLockedById(candidate.id).orElseThrow(()->new BadCredentialsException("Email or password is incorrect."));
        if (!user.active) throw new DisabledException("Account unavailable. Contact campus administration.");
        if (user.lockedUntil!=null && user.lockedUntil.isAfter(Instant.now())) throw new LockedException("Too many attempts. Try again in 15 minutes.");
        String portal="user";
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes context) portal=context.getRequest().getParameter("portal");
        boolean rightPortal="ADMIN".equals(user.role)?"admin".equalsIgnoreCase(portal):!"admin".equalsIgnoreCase(portal);
        if (!passwords.matches(String.valueOf(input.getCredentials()),user.passwordHash)) {
            user.failedAttempts++;
            if (user.failedAttempts>=5) { user.lockedUntil=Instant.now().plusSeconds(900); user.failedAttempts=0; }
            users.save(user); support.audit(user,"LOGIN_FAILED","authentication");
            throw new BadCredentialsException("Email or password is incorrect. Check your saved password and try again.");
        }
        if (!rightPortal) throw new BadCredentialsException("ADMIN".equals(user.role)
            ? "Your password is correct. Open Administrator sign in at /admin/login to continue."
            : "Your password is correct. Open Student & staff sign in at /login to continue.");
        user.failedAttempts=0; user.lockedUntil=null; users.save(user); support.audit(user,"USER_LOGIN","authentication");
        return UsernamePasswordAuthenticationToken.authenticated(user.email,null,List.of(new SimpleGrantedAuthority("ROLE_"+user.role)));
    }
    @Override public boolean supports(Class<?> type) { return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type); }
}
