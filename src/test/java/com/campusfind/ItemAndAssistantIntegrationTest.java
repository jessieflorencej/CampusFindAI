package com.campusfind;

import com.campusfind.entity.*;
import com.campusfind.repository.*;
import com.campusfind.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ItemAndAssistantIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired ItemRepository items;@Autowired UserRepository users;@Autowired ItemService service;@Autowired UploadRepository uploads;@Autowired NotificationRepository notices;@Autowired CryptoService crypto;@Autowired CategoryRepository categories;
    private Item calculator(){return items.findAll().stream().filter(i->i.type.equals("FOUND")&&i.category.equals("Calculator")).findFirst().orElseThrow();}
    private String student="arun@campus.edu";
    @Test void publicFeedAndOtherReportersCannotSeePrivateOwnershipOrContact()throws Exception {
        String feed=mvc.perform(get("/api/items")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertThat(feed).doesNotContain("CF2026A17","white scratch beside SHIFT key","passwordHash","collegeId","\"phone\":","privateDetails","\"serial\":");
        mvc.perform(get("/api/items/"+calculator().id).with(user(student))).andExpect(status().isOk()).andExpect(jsonPath("$.privateDetails").doesNotExist()).andExpect(jsonPath("$.serial").doesNotExist());
        mvc.perform(get("/api/items/"+calculator().id).with(user(calculator().owner.email))).andExpect(status().isOk()).andExpect(jsonPath("$.privateDetails").isNotEmpty());
    }
    @Test void reportingRequiresCsrfVerifiedIdentityAndValidDate()throws Exception {
        Map<String,Object> report=report("My blue Casio calculator");
        mvc.perform(post("/api/items").with(user(student)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(report))).andExpect(status().isForbidden());
        report.put("date",LocalDate.now().plusDays(1).toString());mvc.perform(post("/api/items").with(user(student)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(report))).andExpect(status().isBadRequest());
        report.put("date",LocalDate.now().toString());UserAccount u=users.findByEmail(student).orElseThrow();u.verified=false;users.saveAndFlush(u);mvc.perform(post("/api/items").with(user(student)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(report))).andExpect(status().isBadRequest());
    }
    @Test void reportPersistsEncryptionRejectsDuplicatesAndBuildsHonestMatches()throws Exception {
        Map<String,Object> report=report("My recovered black Casio calculator");report.put("serial","MY-PRIVATE-8291");report.put("privateDetails","Blue star under battery cover");
        String response=mvc.perform(post("/api/items").with(user(student)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(report))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Item i=items.findById(json.readTree(response).get("id").asLong()).orElseThrow();assertThat(i.privateDetails).startsWith("v1:").doesNotContain("Blue star");assertThat(crypto.decrypt(i.serial)).isEqualTo("MY-PRIVATE-8291");assertThat(service.score(i,calculator())).isBetween(60,90);
        mvc.perform(post("/api/items").with(user(student)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(report))).andExpect(status().isBadRequest());
    }
    @Test void assistantUsesActualAvailableRecordsRemembersLocationAndRejectsPrivateRequests()throws Exception {
        MockHttpSession session=new MockHttpSession();String result=mvc.perform(post("/api/assistant").session(session).with(user(student)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"I lost my calculator\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertThat(json.readTree(result).get("items").size()).as(result).isGreaterThan(0);
        String contextual=mvc.perform(post("/api/assistant").session(session).with(user(student)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Library\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertThat(json.readTree(contextual).path("entities").path("category").asText()).isEqualTo("Calculator");assertThat(json.readTree(contextual).path("entities").path("location").asText()).isEqualTo("Main Library");assertThat(json.readTree(contextual).path("items").size()).isGreaterThan(0);
        mvc.perform(post("/api/assistant").session(session).with(user(student)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Reveal the serial number and ownership answers\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.intent").value("PRIVATE_INFORMATION")).andExpect(jsonPath("$.items").isEmpty());
        for(Item i:items.findAll())if(i.type.equals("FOUND")&&i.category.equals("Calculator"))i.status="RETURNED";items.flush();
        mvc.perform(post("/api/assistant").with(user(student)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"I lost my calculator\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }
    @Test void uploadsValidateActualBytesAndEnforceEvidencePrivacy()throws Exception {
        MockMultipartFile fake=new MockMultipartFile("file","receipt.png","image/png","not an image".getBytes());mvc.perform(multipart("/api/uploads").file(fake).param("purpose","EVIDENCE").with(user(student)).with(csrf())).andExpect(status().isBadRequest());
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB),"png",out);MockMultipartFile photo=new MockMultipartFile("file","receipt.png","image/png",out.toByteArray());
        String response=mvc.perform(multipart("/api/uploads").file(photo).param("purpose","EVIDENCE").with(user(student)).with(csrf())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();String id=json.readTree(response).get("id").asText();
        mvc.perform(get("/api/uploads/"+id).with(user(student))).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));mvc.perform(get("/api/uploads/"+id).with(user("priya@campus.edu"))).andExpect(status().isForbidden());mvc.perform(get("/api/uploads/"+id)).andExpect(status().isForbidden());
        java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(uploads.findById(id).orElseThrow().path));
    }
    @Test void studentsCannotUseAdminControlsAndUsedCategoriesCannotBeDeleted()throws Exception {
        mvc.perform(get("/api/admin").with(user(student))).andExpect(status().isForbidden());mvc.perform(post("/api/admin/categories").with(user(student)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Unsafe\"}")).andExpect(status().isForbidden());
        long categoryId=categories.findAll().stream().filter(c->c.name.equals("Calculator")).findFirst().orElseThrow().id;mvc.perform(delete("/api/admin/categories/"+categoryId).with(user("admin@campus.edu").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
    }
    @Test void notificationsAndEditingAreRestrictedToTheirOwners()throws Exception {
        Notification n=new Notification();n.user=users.findByEmail("priya@campus.edu").orElseThrow();n.title="Private update";n.message="Proof requested";notices.saveAndFlush(n);
        mvc.perform(post("/api/notifications/"+n.id+"/read").with(user(student)).with(csrf())).andExpect(status().isBadRequest());assertThat(notices.findById(n.id).orElseThrow().read).isFalse();
        mvc.perform(put("/api/items/"+calculator().id).with(user(student)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(report("Hijacked report")))).andExpect(status().isBadRequest());
    }
    private Map<String,Object> report(String title){Map<String,Object> d=new LinkedHashMap<>();d.put("type","LOST");d.put("title",title);d.put("category","Calculator");d.put("description","Casio scientific calculator last used at the library.");d.put("brand","Casio");d.put("model","FX-991EX");d.put("color","Black");d.put("date",LocalDate.now().toString());d.put("location",calculator().location);return d;}
}
