package com.campusfind.service;

import com.campusfind.entity.*;
import com.campusfind.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;

@Service
public class ItemService {
    private final ItemRepository items;
    private final UploadRepository uploads;
    private final CategoryRepository categories;
    private final LocationRepository locations;
    private final MatchRepository matches;
    private final ClaimRepository claims;
    private final CryptoService crypto;
    private final Support support;
    public ItemService(ItemRepository items, UploadRepository uploads, CategoryRepository categories, LocationRepository locations, MatchRepository matches, ClaimRepository claims, CryptoService crypto, Support support) {
        this.items=items;this.uploads=uploads;this.categories=categories;this.locations=locations;this.matches=matches;this.claims=claims;this.crypto=crypto;this.support=support;
    }
    public static boolean open(Item i) { return !Set.of("RETURNED","CLOSED","REMOVED").contains(i.status); }
    public static String text(Map<String,?> data,String key) { Object x=data.get(key);return x==null?"":x.toString().trim(); }
    public static void check(boolean test,String message) { if(!test) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    public Item get(long id) { return items.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Item not found")); }
    public Map<String,Object> publicView(Item i) {
        Map<String,Object> v=new LinkedHashMap<>();
        v.put("id",i.id);v.put("type",i.type);v.put("title",i.title);v.put("category",i.category);v.put("description",i.description);v.put("brand",i.brand);v.put("model",i.model);v.put("color",i.color);v.put("date",i.date);v.put("time",i.time);v.put("location",i.location);v.put("status",i.status);v.put("createdAt",i.createdAt);
        v.put("images", uploads.findByItemId(i.id).stream().filter(u->"ITEM".equals(u.purpose)).map(u->Map.of("id",u.id,"url","/api/uploads/"+u.id)).toList());return v;
    }
    public Map<String,Object> view(Item i,UserAccount user) {Map<String,Object> v=publicView(i);v.put("ownerMine",user!=null&&i.owner.id.equals(user.id));return v;}
    public List<Map<String,Object>> search(Map<String,String> q,UserAccount user) {
        String query=q.getOrDefault("query","").toLowerCase(Locale.ROOT);
        return items.findAll().stream().filter(i->!"REMOVED".equals(i.status)).filter(i->query.isBlank()||Arrays.stream(query.split("\\s+")).allMatch(t->searchText(i).contains(t)))
            .filter(i->filter(q,"type",i.type)&&filter(q,"category",i.category)&&filter(q,"brand",i.brand)&&filter(q,"color",i.color)&&filter(q,"location",i.location)&&filter(q,"status",i.status))
            .filter(i->q.getOrDefault("from","").isBlank()||!i.date.isBefore(parseDate(q.get("from"))))
            .filter(i->q.getOrDefault("to","").isBlank()||!i.date.isAfter(parseDate(q.get("to"))))
            .sorted(Comparator.comparing((Item i)->i.createdAt).reversed()).limit(200).map(i->view(i,user)).toList();
    }
    public static String searchText(Item i) {return String.join(" ",safe(i.title),safe(i.category),safe(i.brand),safe(i.model),safe(i.color),safe(i.location),safe(i.description)).toLowerCase(Locale.ROOT);}
    private static String safe(String s){return s==null?"":s;}
    private boolean filter(Map<String,String> q,String key,String actual){String v=q.getOrDefault(key,"");return v.isBlank()||v.equalsIgnoreCase("all")||safe(actual).toLowerCase(Locale.ROOT).contains(v.toLowerCase(Locale.ROOT));}
    private LocalDate parseDate(String s){try{return LocalDate.parse(s);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid date");}}
    @Transactional
    public Map<String,Object> save(Long id,Map<String,Object> data,UserAccount user) {
        check(user.verified,"Verify your email address and college ID before reporting an item.");
        Item i=id==null?new Item():items.findLockedById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Item not found"));
        if(id!=null){check(i.owner.id.equals(user.id)||"ADMIN".equals(user.role),"Only the reporter can edit this item");check(open(i)&&claims.findByItemId(id).isEmpty(),"Items with claims or closed reports cannot be edited");}
        String type=text(data,"type"),title=text(data,"title"),category=text(data,"category"),location=text(data,"location"),description=text(data,"description");
        check(Set.of("LOST","FOUND").contains(type),"Choose lost or found");check(title.length()>=3&&title.length()<=100,"Item name must contain 3–100 characters");
        check(description.length()>=10&&description.length()<=1500,"Public description must contain 10–1500 characters");
        check(categories.findAll().stream().anyMatch(c->c.name.equals(category)),"Choose a campus category");check(locations.findAll().stream().anyMatch(l->l.name.equals(location)),"Choose a campus location");
        LocalDate date=parseDate(text(data,"date"));check(!date.isAfter(LocalDate.now())&&!date.isBefore(LocalDate.now().minusYears(10)),"Choose a date within the past 10 years");
        check(items.findAll().stream().noneMatch(x->!Objects.equals(x.id,id)&&x.owner.id.equals(user.id)&&open(x)&&x.type.equals(type)&&x.title.equalsIgnoreCase(title)&&x.date.equals(date)),"A matching report already exists in My Items");
        Map<String,Integer> limits=Map.of("brand",100,"model",100,"color",60,"time",20,"building",120,"floor",50,"room",100,"lastSeen",180,"storageLocation",150);
        limits.forEach((key,max)->check(text(data,key).length()<=max,key+" is too long"));
        if(!text(data,"time").isBlank())try{LocalTime.parse(text(data,"time"));}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid time");}
        String privateDetails=text(data,"privateDetails"),serial=text(data,"serial");check(privateDetails.length()<=4000&&serial.length()<=160,"Private details are too long");
        if(!serial.isBlank())check(!description.toLowerCase().contains(serial.toLowerCase())&&!title.toLowerCase().contains(serial.toLowerCase()),"Remove the serial number from the public description and name");
        if(privateDetails.length()>8)check(!description.toLowerCase().contains(privateDetails.toLowerCase()),"Keep ownership details in the private section");
        List<String> imageIds=data.get("imageIds") instanceof List<?> list?list.stream().map(Object::toString).distinct().toList():List.of();check(imageIds.size()<=5,"Use at most five item photos");
        List<Upload> selected=new ArrayList<>();for(String imageId:imageIds){Upload u=uploads.findById(imageId).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Image not found"));check(u.owner.id.equals(user.id)&&"ITEM".equals(u.purpose)&&u.claimId==null&&(u.itemId==null||u.itemId.equals(id)),"Choose your own unattached item photos");selected.add(u);}
        i.owner=id==null?user:i.owner;i.type=type;i.title=title;i.category=category;i.description=description;i.date=date;i.location=location;
        i.brand=text(data,"brand");i.model=text(data,"model");i.color=text(data,"color");i.time=text(data,"time");i.building=text(data,"building");i.floor=text(data,"floor");i.room=text(data,"room");i.lastSeen=text(data,"lastSeen");i.storageLocation=text(data,"storageLocation");
        try{i.value=text(data,"value").isBlank()?BigDecimal.ZERO:new BigDecimal(text(data,"value"));check(i.value.signum()>=0&&i.value.compareTo(new BigDecimal("10000000"))<=0,"Value must be between 0 and 10000000");}catch(NumberFormatException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid item value");}
        if(id==null||data.containsKey("privateDetails"))i.privateDetails=crypto.encrypt(privateDetails);if(id==null||data.containsKey("serial"))i.serial=crypto.encrypt(serial);i.updatedAt=Instant.now();if(id==null){i.createdAt=Instant.now();i.status=type.equals("LOST")?"LOST":"AVAILABLE_FOR_CLAIM";}
        items.saveAndFlush(i);if(id!=null&&data.containsKey("imageIds")){for(Upload old:uploads.findByItemId(id)){if(!imageIds.contains(old.id)){old.itemId=null;uploads.save(old);}}}
        for(Upload u:selected){u.itemId=i.id;uploads.save(u);}support.audit(user,id==null?"REPORT_CREATED":"REPORT_UPDATED","item:"+i.id);refreshMatches(i);return view(i,user);
    }
    @Transactional
    public void close(long id,UserAccount user){Item i=items.findLockedById(id).orElseThrow();check(i.owner.id.equals(user.id)||"ADMIN".equals(user.role),"Only the reporter can close this item");check(claims.findByItemId(id).stream().noneMatch(c->!Set.of("REJECTED","CLOSED","RETURN_CONFIRMED").contains(c.status)),"Resolve the active claims before closing this report");i.status="CLOSED";items.save(i);support.audit(user,"REPORT_CLOSED","item:"+id);}
    @Transactional
    public void refreshMatches(Item changed){
        for(Item other:items.findAll()) {if(other.id.equals(changed.id)||other.type.equals(changed.type)||!open(other)||!open(changed))continue;Item lost="LOST".equals(changed.type)?changed:other,found="FOUND".equals(changed.type)?changed:other;
            int score=score(lost,found);Optional<ItemMatch> existing=matches.findAll().stream().filter(m->m.lostItem.id.equals(lost.id)&&m.foundItem.id.equals(found.id)).findFirst();
            if(score<60){existing.ifPresent(matches::delete);continue;}ItemMatch m=existing.orElseGet(ItemMatch::new);m.lostItem=lost;m.foundItem=found;m.score=score;m.createdAt=Instant.now();matches.save(m);
            if(existing.isEmpty()&&!lost.owner.id.equals(found.owner.id))support.notify(lost.owner,"Possible match found",found.title+" near "+found.location+" — "+score+"% possible match. Review it in Matches.");
            if("LOST".equals(lost.status)){lost.status="POSSIBLE_MATCH";items.save(lost);}
        }
    }
    public int score(Item a,Item b){int s=0;if(eq(a.category,b.category))s+=20;if(eq(a.brand,b.brand))s+=15;if(eq(a.color,b.color))s+=10;if(eq(a.location,b.location))s+=15;
        long days=Math.abs(java.time.temporal.ChronoUnit.DAYS.between(a.date,b.date));s+=days<=1?15:days<=3?12:days<=7?8:days<=30?3:0;
        Set<String> aa=tokens(safe(a.title)+" "+safe(a.model)+" "+safe(a.description)),bb=tokens(safe(b.title)+" "+safe(b.model)+" "+safe(b.description));long common=aa.stream().filter(bb::contains).count();s+=(int)Math.round(15.0*common/Math.max(1,Math.min(aa.size(),bb.size())));
        // No image points are fabricated: the remaining ten points are reserved for a future visual provider.
        return Math.min(90,s);
    }
    private boolean eq(String a,String b){return a!=null&&!a.isBlank()&&a.equalsIgnoreCase(b);}
    private Set<String> tokens(String value){return new HashSet<>(Arrays.asList(value.toLowerCase().replaceAll("[^a-z0-9 ]"," ").split("\\s+")));}
    public List<Map<String,Object>> matches(UserAccount user){return matches.findAll().stream().filter(m->open(m.lostItem)&&open(m.foundItem)).filter(m->"ADMIN".equals(user.role)||m.lostItem.owner.id.equals(user.id)||m.foundItem.owner.id.equals(user.id)).sorted(Comparator.comparingInt((ItemMatch m)->m.score).reversed()).map(m->{Map<String,Object> v=new LinkedHashMap<>();v.put("id",m.id);v.put("score",m.score);v.put("lost",publicView(m.lostItem));v.put("found",publicView(m.foundItem));return v;}).toList();}
}
