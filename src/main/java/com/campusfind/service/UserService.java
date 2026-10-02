package com.campusfind.service;

import com.campusfind.entity.UserAccount;
import com.campusfind.repository.UserRepository;
import com.campusfind.repository.UploadRepository;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
public class UserService {
    public record RegisterRequest(@NotBlank @Size(max=100) String name,@NotBlank @Email @Size(max=190) String email,@NotBlank @Size(max=60) String collegeId,@NotBlank @Size(max=100) String department,@NotBlank @Size(max=30) String year,@NotBlank @Pattern(regexp="[+0-9 ()-]{7,25}") String phone,@NotBlank String password) {}
    public record ProfileRequest(@NotBlank @Size(max=100) String name,@NotBlank @Size(max=100) String department,@NotBlank @Size(max=30) String year,@NotBlank @Pattern(regexp="[+0-9 ()-]{7,25}") String phone,@Size(max=36) String profileImageId) {}
    private final UserRepository users;
    private final UploadRepository uploads;
    private final PasswordEncoder passwords;
    private final CryptoService crypto;
    private final CampusMailService mail;
    private final Support support;
    private final String baseUrl;
    public UserService(UserRepository users,UploadRepository uploads,PasswordEncoder passwords,CryptoService crypto,CampusMailService mail,Support support,@Value("${campus.base-url:http://localhost:8080}") String baseUrl) {this.users=users;this.uploads=uploads;this.passwords=passwords;this.crypto=crypto;this.mail=mail;this.support=support;this.baseUrl=baseUrl;}

    public static void validatePassword(String password) {
        if(password==null||password.length()<12||password.length()>72||!password.matches("(?s).*[a-z].*")||!password.matches("(?s).*[A-Z].*")||!password.matches("(?s).*[0-9].*")||!password.matches("(?s).*[^a-zA-Z0-9].*")) throw bad("Use 12–72 characters including uppercase, lowercase, a number and a symbol.");
        if(password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw bad("Password must fit within 72 UTF-8 bytes.");
    }
    @Transactional public void register(RegisterRequest r) {
        validatePassword(r.password());String email=r.email().trim().toLowerCase(Locale.ROOT);

        if(users.existsByEmail(email)||users.existsByCollegeId(r.collegeId().trim())) throw bad("An account with those details already exists. Try signing in or contact campus administration.");
        UserAccount u=new UserAccount();u.name=r.name().trim();u.email=email;u.collegeId=r.collegeId().trim();u.department=r.department().trim();u.year=r.year().trim();u.phone=r.phone().trim();u.passwordHash=passwords.encode(r.password());u.role="USER";
        users.save(u);sendVerification(u);support.audit(u,"USER_REGISTERED","account:"+u.id);
        support.notify(u,"Welcome to CampusFind AI","Verify your email address and ask the campus desk to check your college ID before making ownership claims.");
    }
    @Transactional public void sendVerification(UserAccount user) {
        UserAccount u=users.findLockedById(user.id).orElseThrow();
        if(u.emailVerified) return;
        if(u.verificationExpiresAt!=null&&u.verificationExpiresAt.isAfter(Instant.now().plusSeconds(23*3600+59*60))) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Please wait a minute before requesting another verification email.");
        String token=crypto.token();u.verificationTokenHash=crypto.hash(token);u.verificationExpiresAt=Instant.now().plusSeconds(86400);users.save(u);
        mail.send(u.email,"Verify your CampusFind AI email","Hi "+u.name+",\n\nVerify your email address using the link below. This link expires in 24 hours.\n"+baseUrl+"/verify-email?token="+token+"\n\nCampus administration must also check your college ID before ownership claims are enabled.");
    }
    @Transactional public void verifyEmail(String token) {
        if(token==null||token.length()>100) throw bad("This verification link is invalid or expired.");
        UserAccount candidate=users.findByVerificationTokenHash(crypto.hash(token)).orElseThrow(()->bad("This verification link is invalid or expired."));
        UserAccount u=users.findLockedById(candidate.id).orElseThrow();
        if(u.verificationTokenHash==null||!crypto.matches(token,u.verificationTokenHash)||u.verificationExpiresAt==null||u.verificationExpiresAt.isBefore(Instant.now())) throw bad("This verification link is invalid or expired.");
        u.emailVerified=true;u.verified=u.identityVerified;u.verificationTokenHash=null;u.verificationExpiresAt=null;users.save(u);support.audit(u,"EMAIL_VERIFIED","account:"+u.id);
        support.notify(u,"Email verified",u.verified?"Your campus account is verified and ready for ownership claims.":"Your email is verified. Visit the campus desk for your college ID check before making claims.");
    }
    @Transactional public void forgotPassword(String email) {
        if(email==null||email.length()>190) return;
        UserAccount candidate=users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).orElse(null);if(candidate==null||!candidate.active)return;
        UserAccount u=users.findLockedById(candidate.id).orElseThrow();
        if(u.resetExpiresAt!=null&&u.resetExpiresAt.isAfter(Instant.now().plusSeconds(29*60)))return;
        String token=crypto.token();u.resetTokenHash=crypto.hash(token);u.resetExpiresAt=Instant.now().plusSeconds(1800);users.save(u);
        mail.send(u.email,"Reset your CampusFind AI password","A password reset was requested for your campus account. This one-time link expires in 30 minutes.\n"+baseUrl+"/reset-password?token="+token+"\n\nIf you did not request this, you can ignore this message.");
        support.audit(u,"PASSWORD_RESET_REQUESTED","account:"+u.id);
    }
    @Transactional public void resetPassword(String token,String password) {
        validatePassword(password);if(token==null||token.length()>100)throw bad("This reset link is invalid or expired.");
        UserAccount candidate=users.findByResetTokenHash(crypto.hash(token)).orElseThrow(()->bad("This reset link is invalid or expired."));
        UserAccount u=users.findLockedById(candidate.id).orElseThrow();
        if(!u.active||u.resetTokenHash==null||!crypto.matches(token,u.resetTokenHash)||u.resetExpiresAt==null||u.resetExpiresAt.isBefore(Instant.now()))throw bad("This reset link is invalid or expired.");
        u.passwordHash=passwords.encode(password);u.authVersion++;u.resetTokenHash=null;u.resetExpiresAt=null;u.failedAttempts=0;u.lockedUntil=null;users.save(u);support.audit(u,"PASSWORD_RESET","account:"+u.id);
    }
    @Transactional public Map<String,Object> profile(UserAccount user,ProfileRequest r) {
        UserAccount u=users.findLockedById(user.id).orElseThrow();u.name=r.name().trim();u.department=r.department().trim();u.year=r.year().trim();u.phone=r.phone().trim();
        if(r.profileImageId()!=null&&!r.profileImageId().isBlank()) {
            var photo=uploads.findById(r.profileImageId()).orElseThrow(()->bad("Profile image was not found."));
            if(!photo.owner.id.equals(u.id)||!"PROFILE".equals(photo.purpose)||photo.itemId!=null||photo.claimId!=null)throw bad("Choose a profile image uploaded by you.");
            u.profileImageId=photo.id;
        } else {
            u.profileImageId=null;
        }
        users.save(u);support.audit(u,"PROFILE_UPDATED","account:"+u.id);return safe(u);
    }
    @Transactional public void changePassword(UserAccount user,String current,String replacement) {
        validatePassword(replacement);UserAccount u=users.findLockedById(user.id).orElseThrow();
        if(current==null||!passwords.matches(current,u.passwordHash))throw bad("The current password is incorrect.");
        u.passwordHash=passwords.encode(replacement);u.authVersion++;u.resetTokenHash=null;u.resetExpiresAt=null;users.save(u);support.audit(u,"PASSWORD_CHANGED","account:"+u.id);
    }
    public Map<String,Object> safe(UserAccount u) {
        Map<String,Object> result=new LinkedHashMap<>();result.put("id",u.id);result.put("name",u.name);result.put("email",u.email);result.put("collegeId",u.collegeId);result.put("department",u.department);result.put("year",u.year);result.put("phone",u.phone);result.put("role",u.role);result.put("verified",u.verified);result.put("emailVerified",u.emailVerified);result.put("identityVerified",u.identityVerified);result.put("active",u.active);result.put("flagged",u.flagged);result.put("reputation",u.reputation);result.put("profileImageId",u.profileImageId);result.put("createdAt",u.createdAt);return result;
    }
    private static ResponseStatusException bad(String message) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
