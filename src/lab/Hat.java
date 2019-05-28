package lab;

import lab.json.*;

import java.io.Serializable;
import java.time.ZonedDateTime;

public class Hat  implements Serializable, Comparable<Hat> {
    public String color;
    public int size;
    public int num;
    private String user;
    private ZonedDateTime createdDate = ZonedDateTime.now();
    public Thing[] content;

    public String getHatColor(){
        return ("шляпа с цветом " + this.color);
    }
    @Override
    public int compareTo(Hat o){return getNum() - o.getNum();}

    /**
     * Добавляет предмет в шляпу
     * @param obj предмет, который нужно добавить
     */
    public void addthing(Thing obj){
        if (checkspace()!=-1){
            if (checkitem(obj)==-1){
            System.out.println("Объект " + obj.rus(obj.name.toString()) + " был успешно добавлен в шляпу.");
            this.content[checkspace()]=obj;}
            else {System.out.println("Объект " + obj.rus(obj.name.toString()) +" уже есть в этой шляпе");}
        }
        else {
            System.out.println("В шляпе не осталось места. Пожалуйста удалите какой-нибудь предмет прежде чем добавлять новый.\n" +
                    "Объект" + obj.rus(obj.name.toString()) + "не был добавлен в шляпу.");
        }
    }

    /**
     * Проверяет есть ли в шляпе свободное место
     * @return индекс ближайшей свободной ячейки; -1, если свободного места не осталось
     */
    public int checkspace(){
        for (int i=0; i < this.size; i++){
            if (this.content[i]==null){return i;}
        }
        return -1;
    }

    /**
     * Метод для того чтобы узнать только содержимое шляпы
     * @return строку в которой перечисленно все содержимое шляпы
     */
    public String contentlist(){
        StringBuilder result = new StringBuilder();
        result.append(" ");
        for (int i=0; i < this.size; i++) {
            if (this.content[i]!=null)
                result.append(this.content[i].name.toString()).append(", ");
        }
        return result.toString();
    }

    /**
     * Проверяет есть ли заданный предмет в шляпе
     * @param item предмет, наличие которого нужно проверить
     * @return индекс найденного предмета; -1, если предмета в шляпе нет
     */
    private int checkitem(Thing item){
        for (int i=0; i < this.size; i++){
            if (this.content[i]!=null)
                if ((this.content[i].name).equals(item.name)){return i;}
        }
        return -1;
    }

    /**
     * Удаляет предмет из шляпы
     * @param obj предмет, который нужно удалить
     */
    public void deletething(Thing obj) {
        for (int i=0; i < this.size; i++){
            if (this.content[i]!=null)
                if ((this.content[i].name).equals(obj.name)){this.content[i]=null;}
        }
    }

    /**
     * Выводит информацию о шляпе: размер, цвет, местоположение, дату создания и содержимое
     */
    public String showHat(){
        StringBuilder result= new StringBuilder();
        result.append("Размер шляпы ").append(this.size).append("; Цвет шляпы ").append(this.color).append("; Расположение шляпы: полка №").append(this.num).append(";").append(" Дата создания: ").append(this.createdDate);
        for (int i=0; i < this.size; i++){
            if (this.content[i]!=null)
                result.append("В шляпе лежит ").append(this.content[i].rus(this.content[i].name.toString())).append("\n ");
        }
    return result.toString();
    }

    public Hat(int a, String c, int x){
        this.size=a;
        this.num=x;
        this.color=c;
        this.content= new Thing[a];
    }

    public Hat(int a, String c, int x, Thing[] arr){
        this.size=a;
        this.num=x;
        this.color=c;
        this.content=arr;
    }

    Hat(int a, int x, JSONString c){
        this.num=x;
        this.size=a;
        this.color=c.toString();
        this.content= new Thing[a];

    }

    public int getSize() { return size; }

    public String getUser() {
        return user;
    }

    public void setUser(String name){ this.user=name;}

    public void setCreatedDate(ZonedDateTime date){this.createdDate=date;}

    public String getColor() { return color; }

    public int getNum(){return num;}

    public Thing[] getContent() { return content; }

    public ZonedDateTime getCreatedDate(){return createdDate;}
}

