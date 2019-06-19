package lab;

import lab.json.*;


public class HatMaker {

    private static Hat generate(JSONObject object) throws Exception {
        JSONEntity size = object.getItem("size");
        JSONEntity color = object.getItem("color");
        JSONEntity num = object.getItem("num");

        if (size == null)
            throw new IllegalArgumentException("Требуется параметр 'size', но он не указан");
        if (color == null)
            throw new IllegalArgumentException("Требуется параметр 'color', но он не указан");
        if (num == null)
            throw new IllegalArgumentException("Требуется параметр 'num', но он не указан");
        if (num.toInt(new IllegalArgumentException("Параметр 'num' должен быть числом, но это " + num.getTypeName()))<0)
            throw new NegativeShelfNumberException();
        try {
            Hat hat = new Hat(
                    size.toInt(new IllegalArgumentException("Параметр 'size' должен быть числом, но это " + size.getTypeName())),
                    num.toInt(new IllegalArgumentException("Параметр 'num' должен быть числом, но это " + num.getTypeName())),
                    color.toString(new IllegalArgumentException("Параметр 'color' должен быть строкой, но это " + color.getTypeName())));
            hat.color=hat.color.replaceAll("\"", "");

            JSONEntity contents = object.getItem("contents");
            if (contents != null) {
                JSONArray contentsArray= contents.toArray();
                int k=0;
                while ((hat.checkspace()!=-1)&(k<contentsArray.size()) ){
                    JSONEntity itemindex = contentsArray.getItem(k);
                    JSONObject itemobj = itemindex.toObject();
                    JSONEntity item = itemobj.getItem("Itemname");
                    try {
                        hat.addthing(new Thing(Item.valueOf((item.toString()).replaceAll("\"", ""))));
                    } catch (IllegalArgumentException e) {
                        System.out.println("Введен предмет не из списка");
                    }

                    ++k;
                }
            }
            return hat;
        } catch(NegativeArraySizeException e){System.out.println("размер шляпы не может быть отрицательным числом");
            return new Hat(0,"",0); }
    }

    static Hat[] generate(String json) throws Exception {
        JSONEntity entity;

        try {
            entity = JSONParser.parse(json);
        } catch (JSONParseException e) {
            throw new JSONParseException(e.getMessage());
        }

        if (entity == null)
            throw new IllegalArgumentException("Требуется json-объект, но получен null");

        if (entity.isObject())
            return new Hat[]{generate(entity.toObject())};
        else if (entity.isArray()) {
            JSONArray array = entity.toArray();
            Hat[] hats = new Hat[array.size()];
            for (int i = 0; i < array.size(); i++)
                hats[i] = generate(
                        array.getItem(i).toObject(
                                new IllegalArgumentException(
                                        "Все элементы массива должны быть объектами, но элемент с индексом " + i + " имеет тип " + array.getItem(i).getTypeName()
                                )
                        )
                );
            return hats;
        } else
            throw new IllegalArgumentException("Нужен json-объект или json-массив, но вместо него " + entity.getTypeName());
    }
}