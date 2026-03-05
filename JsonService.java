package services;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import models.*;

import java.io.*;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class JsonService {

    // 객체 생성 차단
    private JsonService() {
    }

    // id가 있으면 객체 반환, 아니면 null;
    public static <T> T getById(String id, Class<T> tClass) throws IllegalArgumentException, IOException {
        String path;
        Type type;

        if (tClass == User.class) {
            path = FilePaths.USER_DATA_PATH;
            type = new TypeToken<Map<String, User>>() {}.getType();
        } else if (tClass == Player.class) {
            path = FilePaths.PLAYER_DATA_PATH;
            type = new TypeToken<Map<String, Player>>() {}.getType();
        } else if (tClass == Monster.class) {
            path = FilePaths.MONSTER_DATA_PATH;
            type = new TypeToken<Map<String, Monster>>() {}.getType();
        } else {
            throw new IllegalArgumentException("지원하지 않는 타입: " + tClass.getSimpleName());
        }

        FileReader reader = new FileReader(path);

        Gson gson = new Gson();
        Map<String, T> object = gson.fromJson(reader, type);

        reader.close();
        T result = object.get(id);

        if (tClass == Player.class && result != null) {
            Player player = (Player) result;
            player.setItems(getTypedItems(player));
        }

        return result;
    }


    public static <T> T saveById(String id, T data, Class<T> tClass) throws IOException, IllegalArgumentException {

        String path;
        Type type;
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Map<String, ?> object;

        if (tClass == User.class) {
            path = FilePaths.USER_DATA_PATH;
            type = new TypeToken<Map<String, User>>() {
            }.getType();
        } else if (tClass == Player.class) {
            path = FilePaths.PLAYER_DATA_PATH;
            type = new TypeToken<Map<String, Player>>() {
            }.getType();
        } else
            throw new IllegalArgumentException("지원하지 않는 타입 : " + tClass.getSimpleName());

        try (FileReader reader = new FileReader(path)) {
            object = gson.fromJson(reader, type);
        }

        if (object == null)
            throw new EOFException("파일이 비어있습니다.");

        @SuppressWarnings("unchecked")
        Map<String, T> map = (Map<String, T>) object;

        map.put(id, data);

        try (FileWriter writer = new FileWriter(path)) {
            gson.toJson(object, writer);
        }


        return map.get(id);
    }

    public static Item[] getTypedItems(Player player) throws IOException {

        Item[] items = Arrays.stream(player.getItems())
                .map(JsonService::itemFactory)
                .toArray(Item[]::new);

        return items;
    }

    private static Item itemFactory(Item item) {
        ItemName name = item.getName();
        int count = item.getCount();

        return switch (name) {
            case ItemName.hpPotion, ItemName.largeHpPotion -> new Potion(name, count);
            case ItemName.dagger, ItemName.longsword -> new Weapon(name, count);
            case ItemName.shield, ItemName.armor -> new Defensive(name, count);
            case ItemName.bomb -> new Bomb(name, count);
            case ItemName.bossKey -> new Item(name, count);
            default -> throw new IllegalArgumentException("존재하지 않는 아이템 이름 " + name);
        };
    }

    /**
     * ITEM_JSON_PATH에 지정된 JSON 파일을 읽어서 DETAIL_MAP을 채웁니다.
     * JSON 형식은 대략 다음과 같다고 가정합니다:
     *
     * {
     *   "hpPotion": { "name": "hpPotion", "kor": "체력 포션", "weight": 1, "stats": 30,
     *                 "shopInfo": { "buyPrice": 100, "cellPrice": 50 } },
     *   "shield":   { ... },
     *   ...
     * }
     */
    public static Map<ItemName, ItemDetail> getItemDetailMap() throws IOException {
        Type type;
        Gson gson = new Gson();

        // Player 클래스는 ItemDetail도 읽기
        Map<String, ItemDetail> rawDetailMap;
        try (FileReader reader2 = new FileReader(FilePaths.ITEM_DATA_PATH)) {
            type = new TypeToken<Map<String, ItemDetail>>() {
            }.getType();
            rawDetailMap = gson.fromJson(reader2, type);
        }

        Map<ItemName, ItemDetail> detailMap = new HashMap<>();
        for (Map.Entry<String, ItemDetail> entry : rawDetailMap.entrySet()) {
            try {
                ItemName name = ItemName.valueOf(entry.getKey());
                detailMap.put(name, entry.getValue());
            } catch (IllegalArgumentException e) {
                System.out.println("알 수 없는 아이템 이름 : " + entry.getKey());
            }
        }

        return detailMap;
    }

}