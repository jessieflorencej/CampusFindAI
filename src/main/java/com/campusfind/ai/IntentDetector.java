package com.campusfind.ai;
import org.springframework.stereotype.Component;
@Component
public class IntentDetector {
    public String detect(String text){String s=text.toLowerCase();if(s.matches(".*(serial|imei|hidden mark|private detail|ownership answer|phone number|email address|receipt|student id).*"))return "PRIVATE_INFORMATION";if(s.matches(".*(claim status|my claims|check.*claim|handover).*"))return "CHECK_CLAIM";if(s.matches(".*(how.*report|report a lost|report lost).*"))return "REPORT_LOST";if(s.matches(".*(i found|i have found|report found|report a found).*"))return "REPORT_FOUND";if(s.matches("^(hi|hello|hey|help|what can you do)[!.? ]*$"))return "HELP";return "SEARCH_FOUND_ITEM";}
}
