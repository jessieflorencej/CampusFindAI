package com.campusfind.controller;

import com.campusfind.entity.*;
import com.campusfind.repository.*;
import com.campusfind.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ItemController {
    private final ItemService service;private final Support support;private final ItemRepository items;private final ClaimRepository claims;private final NotificationRepository notices;private final CategoryRepository categories;private final LocationRepository locations;private final AnnouncementRepository announcements;private final CryptoService crypto;
    public ItemController(ItemService service,Support support,ItemRepository items,ClaimRepository claims,NotificationRepository notices,CategoryRepository categories,LocationRepository locations,AnnouncementRepository announcements,CryptoService crypto){this.service=service;this.support=support;this.items=items;this.claims=claims;this.notices=notices;this.categories=categories;this.locations=locations;this.announcements=announcements;this.crypto=crypto;}
    @GetMapping("/catalog") public Object catalog(){return Map.of("categories",categories.findAll(),"locations",locations.findAll(),"announcements",announcements.findAll());}
    @GetMapping("/stats") public Object stats(){List<Item> all=items.findAll().stream().filter(i->!"REMOVED".equals(i.status)).toList();long returned=all.stream().filter(i->"RETURNED".equals(i.status)&&"FOUND".equals(i.type)).count();long found=all.stream().filter(i->"FOUND".equals(i.type)).count();return Map.of("reported",all.size(),"returned",returned,"active",all.stream().filter(ItemService::open).count(),"matches",all.stream().filter(i->"POSSIBLE_MATCH".equals(i.status)).count(),"recoveryRate",found==0?0:Math.round(100.0*returned/found));}
    @GetMapping("/items") public Object list(@RequestParam Map<String,String> q,Principal principal){return service.search(q,principal==null?null:support.user(principal));}
    @GetMapping("/items/{id}") public Object detail(@PathVariable long id,Principal p){UserAccount user=p==null?null:support.user(p);Item i=service.get(id);ItemService.check(!"REMOVED".equals(i.status)||user!=null&&"ADMIN".equals(user.role),"This report is no longer available");Map<String,Object> v=service.view(i,user);if(user!=null&&(i.owner.id.equals(user.id)||"ADMIN".equals(user.role))){v.put("privateDetails",crypto.decrypt(i.privateDetails));v.put("serial",crypto.decrypt(i.serial));v.put("building",i.building);v.put("floor",i.floor);v.put("room",i.room);v.put("lastSeen",i.lastSeen);v.put("storageLocation",i.storageLocation);v.put("value",i.value);}v.put("matches",user==null?List.of():service.matches(user).stream().filter(m->((Map<?,?>)m.get("lost")).get("id").equals(id)||((Map<?,?>)m.get("found")).get("id").equals(id)).toList());return v;}
    @PostMapping("/items") public Object create(@RequestBody Map<String,Object> d,Principal p){return service.save(null,d,support.user(p));}
    @PutMapping("/items/{id}") public Object update(@PathVariable long id,@RequestBody Map<String,Object> d,Principal p){return service.save(id,d,support.user(p));}
    @PostMapping("/items/{id}/close") public Object close(@PathVariable long id,Principal p){service.close(id,support.user(p));return Map.of("message","Report closed");}
    @GetMapping("/my-items") public Object mine(Principal p){UserAccount user=support.user(p);return items.findAll().stream().filter(i->i.owner.id.equals(user.id)).sorted(Comparator.comparing((Item i)->i.createdAt).reversed()).map(i->service.view(i,user)).toList();}
    @GetMapping("/matches") public Object matches(Principal p){return service.matches(support.user(p));}
    @GetMapping("/dashboard") public Object dashboard(Principal p){UserAccount user=support.user(p);List<Item> mine=items.findAll().stream().filter(i->i.owner.id.equals(user.id)).toList();List<Map<String,Object>> match=service.matches(user);List<Notification> notes=notes(user);return Map.of("stats",Map.of("lost",mine.stream().filter(i->i.type.equals("LOST")&&ItemService.open(i)).count(),"found",mine.stream().filter(i->i.type.equals("FOUND")&&ItemService.open(i)).count(),"matches",match.size(),"pendingClaims",claims.findByClaimantId(user.id).stream().filter(c->!Set.of("CLOSED","RETURN_CONFIRMED","REJECTED").contains(c.status)).count(),"returned",mine.stream().filter(i->"RETURNED".equals(i.status)).count(),"unread",notes.stream().filter(n->!n.read).count()),"items",mine.stream().map(i->service.view(i,user)).toList(),"matches",match,"activity",notes.stream().map(this::noteView).toList());}
    private List<Notification> notes(UserAccount user){return notices.findAll().stream().filter(n->n.user.id.equals(user.id)).sorted(Comparator.comparing((Notification n)->n.createdAt).reversed()).toList();}
    private Map<String,Object> noteView(Notification n){return Map.of("id",n.id,"title",n.title,"message",n.message,"read",n.read,"createdAt",n.createdAt);}
    @GetMapping("/notifications") public Object notifications(Principal p){return notes(support.user(p)).stream().map(this::noteView).toList();}
    @PostMapping("/notifications/{id}/read") @Transactional public Object read(@PathVariable long id,Principal p){UserAccount u=support.user(p);Notification n=notices.findById(id).orElseThrow();ItemService.check(n.user.id.equals(u.id),"Notification is not yours");n.read=true;notices.save(n);return Map.of("ok",true);}
    @PostMapping("/notifications/read-all") @Transactional public Object readAll(Principal p){for(Notification n:notes(support.user(p))){n.read=true;notices.save(n);}return Map.of("ok",true);}
}
