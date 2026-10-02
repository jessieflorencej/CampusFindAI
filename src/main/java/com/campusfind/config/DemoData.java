package com.campusfind.config;

import com.campusfind.entity.*;
import com.campusfind.repository.*;
import com.campusfind.service.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Explicit local demonstration data, disabled with DEMO_DATA=false for a real deployment. */
@Component @ConditionalOnProperty(name="campus.demo-data",havingValue="true")
public class DemoData implements CommandLineRunner {
    @Value("${campus.demo-student-password:}") private String studentPassword;
    @Value("${campus.demo-admin-password:}") private String adminPassword;
    private final UserRepository users;private final ItemRepository items;private final CategoryRepository categories;private final LocationRepository locations;private final AnnouncementRepository announcements;private final PasswordEncoder passwords;private final CryptoService crypto;private final Support support;private final ItemService itemService;
    public DemoData(UserRepository users,ItemRepository items,CategoryRepository categories,LocationRepository locations,AnnouncementRepository announcements,PasswordEncoder passwords,CryptoService crypto,Support support,ItemService itemService){this.users=users;this.items=items;this.categories=categories;this.locations=locations;this.announcements=announcements;this.passwords=passwords;this.crypto=crypto;this.support=support;this.itemService=itemService;}
    @Override @Transactional public void run(String... args) {
        if(categories.count()==0)for(String name:List.of("Electronics","Mobile Phone","Laptop","Tablet","Calculator","Earphones","Charger","Wallet","ID Card","Keys","Books","Notes","Stationery","Backpack","Water Bottle","Watch","Jewelry","Clothing","Sports Equipment","Documents","Other"))categories.save(new Category(name));
        if(locations.count()==0)for(String name:List.of("Main Library","Engineering Block","CSE Block","ECE Block","Cafeteria","Auditorium","Sports Ground","Hostel","Parking","Administration Block","Laboratory","Bus Stop","Classrooms","Campus Security Office","Library Reception","Campus Lost & Found Desk"))locations.save(new CampusLocation(name));
        if(users.count()>0)return;
        if(studentPassword.isBlank() || adminPassword.isBlank())throw new IllegalStateException("Set CAMPUS_DEMO_STUDENT_PASSWORD and CAMPUS_DEMO_ADMIN_PASSWORD before initializing demo accounts.");
        UserService.validatePassword(studentPassword);UserService.validatePassword(adminPassword);
        UserAccount arun=user("Arun Kumar","arun@campus.edu","CSE2026-017","Computer Science","3","USER",25);
        UserAccount priya=user("Priya Sharma","priya@campus.edu","ECE2026-042","Electronics","2","USER",45);
        UserAccount admin=user("Campus Recovery Team","admin@campus.edu","ADMIN-001","Student Services","Staff","ADMIN",0);
        UserAccount mira=user("Mira Patel","mira@campus.edu","DES2026-009","Design","2","USER",15);
        item(arun,"LOST","Casio FX-991EX calculator","Calculator","Black scientific calculator misplaced after an afternoon study session.","Casio","FX-991EX","Black","Main Library",1,2100,"white scratch beside SHIFT key","CF2026A17");
        item(priya,"FOUND","Casio scientific calculator","Calculator","A black Casio calculator found near the library reading area. Identifying details are held privately.","Casio","FX-991EX","Black","Main Library",1,2100,"white scratch beside SHIFT key","CF2026A17");
        item(priya,"FOUND","Blue insulated water bottle","Water Bottle","Blue metal bottle left at a table after lunch.","Milton","Thermosteel","Blue","Cafeteria",0,850,"small moon sticker under the base","MT8427");
        item(arun,"LOST","Sony wireless headphones","Earphones","Over-ear wireless headphones in a soft carrying sleeve.","Sony","WH-CH720N","Black","Engineering Block",2,8900,"red thread tied to carrying sleeve zip","SN84519");
        item(mira,"FOUND","Brown leather wallet","Wallet","A small brown wallet is secured at the campus lost and found desk.","Fossil","Bifold","Brown","Bus Stop",1,2500,"embroidered green leaf inside left pocket","FW1207");
        item(priya,"FOUND","Silver house keys","Keys","Three keys on a small keyring found near the cycle stand.","","","Silver","Parking",0,300,"brass star charm engraved with number 19","");
        item(mira,"LOST","Navy everyday backpack","Backpack","Navy canvas backpack last seen after the design seminar.","Wildcraft","Daily","Navy","Auditorium",3,1800,"orange notebook and a crochet turtle inside","");
        item(priya,"FOUND","Introduction to Algorithms","Books","A well-used algorithms textbook found on a classroom desk.","MIT Press","4th edition","White","CSE Block",2,1400,"blue dedication on page 3 for Arun","");
        item(arun,"FOUND","White wireless earbuds case","Earphones","Closed white earbud charging case found near the basketball court.","OnePlus","Buds Z2","White","Sports Ground",0,3500,"two tiny dents beside charging port","OPBZ2118");
        item(mira,"LOST","Student identity card","ID Card","Student card with a blue lanyard. Please hand it to the campus desk.","","","Blue","Cafeteria",1,100,"small flower drawn on back of plastic sleeve","");
        Item returned=item(priya,"FOUND","Stainless steel water bottle","Water Bottle","A steel bottle recovered by its owner through the campus desk.","Cello","","Silver","Main Library",8,700,"yellow band around the base","");returned.status="RETURNED";items.save(returned);
        Item closed=item(arun,"LOST","Grey umbrella","Other","Compact umbrella previously misplaced outside the laboratory.","","","Grey","Laboratory",9,450,"teal stitching around handle","");closed.status="CLOSED";items.save(closed);
        items.flush();for(Item i:items.findAll())itemService.refreshMatches(i);
        Announcement welcome=new Announcement();welcome.title="A little help goes a long way";welcome.message="Found something? Bring it to the Campus Lost & Found Desk. Safe handovers are available at the Security Office, Library Reception and Administration Office.";announcements.save(welcome);
        support.notify(arun,"Welcome back, Arun","Your calculator report is live. Findy can search actual found reports and help you begin a secure claim.");
        support.notify(priya,"Thanks for looking out for your campus","You have helped keep found belongings safe. Review ownership claims from your Claims page.");
        support.audit(admin,"DEMO_INITIALIZED","local demonstration dataset");
    }
    private UserAccount user(String name,String email,String collegeId,String department,String year,String role,int reputation){UserAccount u=new UserAccount();u.name=name;u.email=email;u.collegeId=collegeId;u.department=department;u.year=year;u.phone="9000000000";u.passwordHash=passwords.encode(role.equals("ADMIN")?adminPassword:studentPassword);u.role=role;u.verified=true;u.emailVerified=true;u.identityVerified=true;u.reputation=reputation;u.createdAt=Instant.now().minusSeconds(86400L*60);return users.save(u);}
    private Item item(UserAccount user,String type,String title,String category,String description,String brand,String model,String color,String location,int days,int value,String mark,String serial){Item i=new Item();i.owner=user;i.type=type;i.title=title;i.category=category;i.description=description;i.brand=brand;i.model=model;i.color=color;i.location=location;i.building=location;i.floor="Ground floor";i.room="Reading Hall";i.lastSeen=location;i.storageLocation="Campus Security Office";i.date=LocalDate.now().minusDays(days);i.time="15:00";i.value=BigDecimal.valueOf(value);i.privateDetails=crypto.encrypt(mark);i.serial=crypto.encrypt(serial);i.status=type.equals("LOST")?"LOST":"AVAILABLE_FOR_CLAIM";i.createdAt=Instant.now().minusSeconds(days*86400L);i.updatedAt=i.createdAt;return items.save(i);}
}
