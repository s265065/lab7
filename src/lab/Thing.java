package lab;

import java.io.Serializable;

public class Thing implements Serializable
{protected Item name;

String rus(String s){
    switch (s){
        case "TOOTHBRUSH": {return "зубная щётка";}
        case "DENTIFRIECE": return "зубной порошок";
        case "SOAP": return "мыло";
        case "TOWEL": return "полотенце";
        case "CHIEF": return "носовой платок";
        case "SOCKS": return "носки";
        case "NAIL": return "гвоздь";
        case "COPPERWIRE": return "проволока";
    }
return "";}

public Thing(Item itemType){
    this.name=itemType;
}

}
