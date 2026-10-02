package com.campusfind;

import com.campusfind.entity.UserAccount;
import com.campusfind.repository.*;
import com.campusfind.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class AuthenticationIntegrationTest {
    private static final String STUDENT_PASSWORD="Test!aA1-"+UUID.randomUUID();
    private static final String ADMIN_PASSWORD="Test!aA1-"+UUID.randomUUID();
    @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired CryptoService crypto;@Autowired PasswordEncoder passwords;@Autowired UserService userService;
    @BeforeEach void unlockStudent(){UserAccount u=users.findByEmail("arun@campus.edu").orElseThrow();u.failedAttempts=0;u.lockedUntil=null;u.active=true;u.passwordHash=passwords.encode(STUDENT_PASSWORD);users.save(u);UserAccount admin=users.findByEmail("admin@campus.edu").orElseThrow();admin.failedAttempts=0;admin.lockedUntil=null;admin.passwordHash=passwords.encode(ADMIN_PASSWORD);users.save(admin);}
    @Test void csrfIsMandatoryForEveryAuthenticationMutation() throws Exception {
        mvc.perform(post("/api/auth/login").param("username","arun@campus.edu").param("password",STUDENT_PASSWORD)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty()).andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));
    }
    @Test void userCannotEnterAdminPortalOrReadAdminData() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).param("username","arun@campus.edu").param("password",STUDENT_PASSWORD).param("portal","admin")).andExpect(status().isUnauthorized());
        MockHttpSession session=login("arun@campus.edu",STUDENT_PASSWORD,"user");
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.user.name").value("Arun Kumar")).andExpect(jsonPath("$.user.passwordHash").doesNotExist()).andExpect(jsonPath("$.user.resetTokenHash").doesNotExist());
    }
    @Test void adminUsesSeparatePortal() throws Exception {
        for(int i=0;i<6;i++)mvc.perform(post("/api/auth/login").with(csrf()).param("username","admin@campus.edu").param("password",ADMIN_PASSWORD).param("portal","user")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Your password is correct. Open Administrator sign in at /admin/login to continue."));
        UserAccount admin=users.findByEmail("admin@campus.edu").orElseThrow();
        assertThat(admin.failedAttempts).isZero();assertThat(admin.lockedUntil).isNull();
        MockHttpSession session=login("admin@campus.edu",ADMIN_PASSWORD,"admin");
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.user.role").value("ADMIN"));
    }
    @Test void failedLoginsLockTheAccountPersistently() throws Exception {
        for(int i=0;i<5;i++)mvc.perform(post("/api/auth/login").with(csrf()).param("username","arun@campus.edu").param("password","wrong-password").param("portal","user")).andExpect(status().isUnauthorized());
        assertThat(users.findByEmail("arun@campus.edu").orElseThrow().lockedUntil).isAfter(Instant.now());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username","arun@campus.edu").param("password",STUDENT_PASSWORD).param("portal","user")).andExpect(status().isUnauthorized());
    }
    @Test void registrationAcceptsAnyEmailDomainAndNeverGrantsAdminRole() throws Exception {
        String suffix=UUID.randomUUID().toString().substring(0,8);
        String json="{\"name\":\"New Student\",\"email\":\"new"+suffix+"@gmail.com\",\"collegeId\":\"NEW-"+suffix+"\",\"department\":\"CSE\",\"year\":\"1\",\"phone\":\"9000011111\",\"password\":\"StrongPass@123\",\"role\":\"ADMIN\",\"verified\":true}";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());
        UserAccount created=users.findByEmail("new"+suffix+"@gmail.com").orElseThrow();assertThat(created.role).isEqualTo("USER");assertThat(created.verified).isFalse();assertThat(created.emailVerified).isFalse();assertThat(created.verificationTokenHash).hasSize(64);assertThat(created.passwordHash).startsWith("$2a$");
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.replace("@gmail.com","@outlook.com").replace("NEW-","OUTLOOK-"))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.replace("@gmail.com","invalid-email").replace("NEW-","INVALID-"))).andExpect(status().isBadRequest());
    }
    @Test void passwordResetIsOneTimeAndRevokesExistingSessions() throws Exception {
        MockHttpSession oldSession=login("arun@campus.edu",STUDENT_PASSWORD,"user");
        UserAccount u=users.findByEmail("arun@campus.edu").orElseThrow();String token=crypto.token();u.resetTokenHash=crypto.hash(token);u.resetExpiresAt=Instant.now().plusSeconds(300);users.save(u);
        String body="{\"token\":\""+token+"\",\"password\":\"Replacement@123\"}";
        mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(get("/api/profile").session(oldSession)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }
    @Test void emailVerificationExpiresAndNeedsIndependentCollegeIdReview(){
        UserAccount u=users.findByEmail("mira@campus.edu").orElseThrow();String token=crypto.token();u.emailVerified=false;u.identityVerified=false;u.verified=false;u.verificationTokenHash=crypto.hash(token);u.verificationExpiresAt=Instant.now().minusSeconds(1);users.save(u);
        assertThatThrownBy(()->userService.verifyEmail(token)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        u=users.findByEmail("mira@campus.edu").orElseThrow();u.verificationExpiresAt=Instant.now().plusSeconds(300);users.save(u);userService.verifyEmail(token);u=users.findByEmail("mira@campus.edu").orElseThrow();assertThat(u.emailVerified).isTrue();assertThat(u.verified).isFalse();
        assertThatThrownBy(()->userService.verifyEmail(token)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        u.emailVerified=true;u.identityVerified=true;u.verified=true;users.save(u);
    }
    @Test void privateDataUsesAuthenticatedEncryptionAndRandomNonces(){String a=crypto.encrypt("hidden serial");String b=crypto.encrypt("hidden serial");assertThat(a).isNotEqualTo(b).doesNotContain("hidden serial");assertThat(crypto.decrypt(a)).isEqualTo("hidden serial");assertThat(crypto.matches("CF-123456",crypto.hash("CF-123456"))).isTrue();assertThat(crypto.matches("CF-000000",crypto.hash("CF-123456"))).isFalse();assertThatThrownBy(()->crypto.decrypt(a.substring(0,a.length()-4)+"AAAA")).isInstanceOf(IllegalStateException.class);}
    private MockHttpSession login(String email,String password,String portal)throws Exception{return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).param("username",email).param("password",password).param("portal",portal)).andExpect(status().isOk()).andReturn().getRequest().getSession(false);}
}
