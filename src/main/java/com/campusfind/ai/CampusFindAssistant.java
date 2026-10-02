package com.campusfind.ai;
import com.campusfind.entity.*;
import com.campusfind.repository.*;
import com.campusfind.service.*;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpSession;
import java.time.*;
import java.util.*;
@Service
public class CampusFindAssistant {
    private final IntentDetector intents;private final EntityExtractor entities;private final ItemRepository items;private final ItemService itemService;private final ClaimRepository claims;
    public CampusFindAssistant(IntentDetector intents,EntityExtractor entities,ItemRepository items,ItemService itemService,ClaimRepository claims){this.intents=intents;this.entities=entities;this.items=items;this.itemService=itemService;this.claims=claims;}
    public Map<String,Object> answer(String message,HttpSession session,UserAccount user){ItemService.check(message!=null&&!message.isBlank()&&message.length()<=1000,"Enter a message of 1–1000 characters");String intent=intents.detect(message);
        if(intent.equals("PRIVATE_INFORMATION"))return response("Some identifying information is hidden for security. Start an ownership claim if you believe an item belongs to you. I cannot reveal private details or evidence.",intent,List.of(),Map.of());
        if(intent.equals("HELP"))return response("Hi, I’m Findy. Tell me what you lost, its color, and where you last saw it. I search current campus reports and can help you report an item or track a claim.",intent,List.of(),Map.of());
        if(intent.equals("CHECK_CLAIM")){List<Claim> mine=claims.findByClaimantId(user.id);String reply=mine.isEmpty()?"You have no claims yet. Open a found item and choose ‘This might be mine’ to begin.":"You have "+mine.size()+" claim(s). "+mine.stream().sorted(Comparator.comparing((Claim c)->c.createdAt).reversed()).limit(3).map(c->"#"+c.id+" for "+c.item.title+": "+c.status.toLowerCase().replace('_',' ')).reduce((a,b)->a+"; "+b).orElse("")+". Open My Claims for the next step.";return response(reply,intent,List.of(),Map.of());}
        if(intent.equals("REPORT_LOST"))return response("Open Report Lost, add a public description and campus location, then keep serial numbers and unique marks in Private Verification Information. Add up to five photos.",intent,List.of(),Map.of("url","/report/lost"));
        Map<String,String> extracted=entities.extract(message);Map<String,String> context=new LinkedHashMap<>();Object previous=session.getAttribute("findyContext");boolean newSearch=message.toLowerCase().matches(".*(i lost|can't find|cannot find|show |any |i found|search |looking for).*" )||extracted.containsKey("category");
        if(!newSearch&&previous instanceof Map<?,?> prior)prior.forEach((k,v)->context.put(k.toString(),v.toString()));context.putAll(extracted);session.setAttribute("findyContext",context);
        String type=intent.equals("REPORT_FOUND")?"LOST":"FOUND";
        if(context.isEmpty())return response("What kind of item are you looking for? Try ‘black Casio calculator near the library’ or ‘laptops found this week’.",intent,List.of(),context);
        List<Item> relevant=items.findAll().stream().filter(i->i.type.equals(type)&&ItemService.open(i)).filter(i->type.equals("LOST")||Set.of("AVAILABLE_FOR_CLAIM","FOUND_ITEM_REPORTED").contains(i.status))
            .filter(i->!context.containsKey("category")||i.category.equalsIgnoreCase(context.get("category"))).filter(i->!context.containsKey("brand")||Objects.toString(i.brand,"").equalsIgnoreCase(context.get("brand")))
            .filter(i->!context.containsKey("color")||Objects.toString(i.color,"").equalsIgnoreCase(context.get("color"))).filter(i->!context.containsKey("location")||i.location.equalsIgnoreCase(context.get("location")))
            .filter(i->!context.containsKey("from")||!i.date.isBefore(LocalDate.parse(context.get("from")))).sorted(Comparator.comparing((Item i)->i.createdAt).reversed()).limit(8).toList();
        String reply;if(intent.equals("REPORT_FOUND"))reply="Thank you for helping. Report the item using Report Found and keep unique marks private. "+(relevant.isEmpty()?"No current lost reports match those details.":"I found "+relevant.size()+" current lost report(s) matching those details.");else reply=relevant.isEmpty()?"I couldn’t find an unclaimed item matching those details. Try a wider location or date, or create a lost report so new matches can notify you.":"I found "+relevant.size()+" unclaimed "+context.getOrDefault("category","item").toLowerCase()+" report(s)"+(context.containsKey("location")?" near "+context.get("location"):"")+". Open a report to check the public details. "+(!context.containsKey("location")?"Do you remember where you last used it?":"Ownership still requires evidence and human review.");
        return response(reply,intent,relevant.stream().map(i->itemService.publicView(i)).toList(),context);
    }
    private Map<String,Object> response(String reply,String intent,List<Map<String,Object>> results,Map<String,String> entities){return Map.of("reply",reply,"intent",intent,"items",results,"entities",entities,"mode","Local language search");}
}
