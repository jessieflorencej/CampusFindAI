package com.campusfind.ai;
import com.campusfind.repository.*;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
@Component
public class EntityExtractor {
    private final CategoryRepository categories;private final LocationRepository locations;
    public EntityExtractor(CategoryRepository categories,LocationRepository locations){this.categories=categories;this.locations=locations;}
    public Map<String,String> extract(String message){String s=message.toLowerCase(Locale.ROOT);Map<String,String> e=new LinkedHashMap<>();
        categories.findAll().stream().filter(c->s.contains(c.name.toLowerCase())||s.contains(c.name.toLowerCase().replaceAll("s$",""))).findFirst().ifPresent(c->e.put("category",c.name));
        Map<String,String> synonyms=Map.ofEntries(Map.entry("casio","Calculator"),Map.entry("calc","Calculator"),Map.entry("airpods","Earphones"),Map.entry("earbuds","Earphones"),Map.entry("headphone","Earphones"),Map.entry("bottle","Water Bottle"),Map.entry("phone","Mobile Phone"),Map.entry("iphone","Mobile Phone"),Map.entry("macbook","Laptop"),Map.entry("bag","Backpack"),Map.entry("id card","ID Card"),Map.entry("identity card","ID Card"));
        synonyms.forEach((word,cat)->{if(s.matches(".*\\b"+java.util.regex.Pattern.quote(word)+"s?\\b.*"))e.putIfAbsent("category",cat);});
        List<String> placeNames=locations.findAll().stream().map(l->l.name).toList();
        Optional<String> exactPlace=placeNames.stream().filter(n->s.contains(n.toLowerCase())).max(Comparator.comparingInt(String::length));
        Optional<String> inferredPlace=placeNames.stream().sorted(Comparator.comparingInt(String::length)).filter(n->Arrays.stream(n.toLowerCase().split(" ")).filter(t->t.length()>3&&!Set.of("main","block","campus","ground","office","lost","found","desk","reception","room","hall").contains(t)).anyMatch(t->s.matches(".*\\b"+java.util.regex.Pattern.quote(t)+"\\b.*"))).findFirst();
        exactPlace.or(()->inferredPlace).ifPresent(n->e.put("location",n));
        for(String color:List.of("black","white","blue","red","green","pink","silver","grey","gray","brown","yellow","purple","orange"))if(s.matches(".*\\b"+color+"\\b.*"))e.put("color",color);
        for(String brand:List.of("casio","apple","samsung","dell","hp","lenovo","sony","jbl","nike","adidas","bose","titan"))if(s.matches(".*\\b"+brand+"\\b.*"))e.put("brand",brand);
        if(s.contains("yesterday"))e.put("from",LocalDate.now().minusDays(1).toString());else if(s.contains("today"))e.put("from",LocalDate.now().toString());else if(s.contains("this week")||s.contains("last 7 days"))e.put("from",LocalDate.now().minusDays(7).toString());else if(s.contains("this month"))e.put("from",LocalDate.now().minusDays(30).toString());
        return e;
    }
}
