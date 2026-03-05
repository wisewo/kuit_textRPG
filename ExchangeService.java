package services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import models.*;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ExchangeService {
    // 상수 정의
    private static final Scanner SCANNER = new Scanner(System.in);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DISPLAY_ID_START = 101;
    private static final int BACK_TO_PREVIOUS = 0;
    private static final int MENU_BUY_ITEM = 1;
    private static final int MENU_SELL_ITEM = 2;
    private static final int MENU_EXIT = 3;
    private static final int SORT_PRICE_ASC = 1;
    private static final int SORT_PRICE_DESC = 2;
    private static final int FILTER_BY_ITEM = 3;
    private static final int FILTER_BY_USER = 4;

    // Strategy Pattern 인터페이스
    private interface ItemViewStrategy {
        List<ExchangeItem> apply(ExchangeItem[] items);
    }

    // 가격 오름차순, 등록시간 오름차순 정렬 전략
    private class ByPriceThenTimeAsc implements ItemViewStrategy {
        @Override
        public List<ExchangeItem> apply(ExchangeItem[] items) {
            return Arrays.stream(items)
                    .sorted(Comparator
                            .comparingInt(ExchangeItem::getPrice)
                            .thenComparingLong(ExchangeItem::getCreatedAt))
                    .toList();
        }
    }

    // 가격 내림차순, 등록시간 오름차순 정렬 전략 (비싼 것부터, 같은 가격이면 먼저 등록된 것부터)
    private class ByPriceThenTimeDesc implements ItemViewStrategy {
        @Override
        public List<ExchangeItem> apply(ExchangeItem[] items) {
            return Arrays.stream(items)
                    .sorted(Comparator
                            .comparingInt(ExchangeItem::getPrice)
                            .reversed()
                            .thenComparingLong(ExchangeItem::getCreatedAt))
                    .toList();
        }
    }

    // 특정 아이템명으로 필터링하는 전략 (필터링 후 가격 오름차순 정렬)
    private class IsMatchingItem implements ItemViewStrategy {
        private final String itemName;

        private IsMatchingItem(String itemName) {
            this.itemName = itemName;
        }

        @Override
        public List<ExchangeItem> apply(ExchangeItem[] items) {
            return Arrays.stream(items)
                    .filter(item -> item.getName().equals(itemName))
                    .sorted(Comparator
                            .comparingInt(ExchangeItem::getPrice)
                            .thenComparingLong(ExchangeItem::getCreatedAt))
                    .toList();
        }
    }

    // 특정 닉네임으로 필터링하는 전략 (필터링 후 가격 오름차순 정렬)
    private class IsMatchingNickname implements ItemViewStrategy {
        private final String nickname;

        private IsMatchingNickname(String nickname) {
            this.nickname = nickname;
        }

        @Override
        public List<ExchangeItem> apply(ExchangeItem[] items) {
            return Arrays.stream(items)
                    .filter(item -> item.getNickname().equals(nickname))
                    .sorted(Comparator
                            .comparingInt(ExchangeItem::getPrice)
                            .thenComparingLong(ExchangeItem::getCreatedAt))
                    .toList();
        }
    }

    // 인스턴스 변수
    private String userId;
    private String nickname;
    private ExchangeItem[] exchangeItems;
    private int selectedItemIndex = 0;
    private Map<String, Player> playersData;

    /**
     * 사용자로부터 숫자 입력을 받는 공통 메서드 (기본 - 0 이상)
     * @return 유효한 정수 입력값 (0 이상)
     */
    private int getValidNumberInput() {
        return getValidNumberInput(0, Integer.MAX_VALUE, true);
    }

    /**
     * 사용자로부터 숫자 입력을 받는 메서드 (0 허용 여부 지정)
     * @param allowZero 0 허용 여부
     * @return 유효한 정수 입력값
     */
    private int getValidNumberInput(boolean allowZero) {
        return getValidNumberInput(allowZero ? 0 : 1, Integer.MAX_VALUE, allowZero);
    }

    /**
     * 사용자로부터 숫자 입력을 받는 메서드 (범위 지정, 0 허용 안함)
     * @param min 최소값
     * @param max 최대값
     * @return 유효한 정수 입력값
     */
    private int getValidNumberInput(int min, int max) {
        return getValidNumberInput(min, max, false);
    }

    /**
     * 사용자로부터 숫자 입력을 받는 핵심 메서드 (모든 검증 로직 포함)
     * @param min 최소값
     * @param max 최대값
     * @param allowZero 0 허용 여부 (뒤로가기 기능)
     * @return 유효한 정수 입력값
     */
    private int getValidNumberInput(int min, int max, boolean allowZero) {
        while (true) {
            String input = SCANNER.nextLine().trim();
            
            // 1️⃣ 빈 입력 검증
            if (input.isEmpty()) {
                System.out.println("❌ 입력이 비어있습니다. 숫자를 입력해주세요.");
                continue;
            }

            // 2️⃣ 입력 형식 검증 (숫자만 허용)
            if (!input.matches("^-?\\d+$")) {
                System.out.println("❌ 숫자가 아닌 문자가 포함되어 있습니다. 정수만 입력해주세요.");
                continue;
            }

            try {
                // 3️⃣ Long으로 먼저 파싱하여 Integer 범위 검증
                long longValue = Long.parseLong(input);
                
                if (longValue > Integer.MAX_VALUE) {
                    System.out.printf("❌ 입력값이 너무 큽니다. %d 이하의 숫자를 입력해주세요.%n", Integer.MAX_VALUE);
                    continue;
                }
                
                if (longValue < Integer.MIN_VALUE) {
                    System.out.printf("❌ 입력값이 너무 작습니다. %d 이상의 숫자를 입력해주세요.%n", Integer.MIN_VALUE);
                    continue;
                }

                int value = (int) longValue;
                
                // 4️⃣ 0(뒤로가기) 특별 처리
                if (allowZero && value == BACK_TO_PREVIOUS) {
                    return BACK_TO_PREVIOUS;
                }
                
                // 5️⃣ 음수 검증 (0이 허용되고 BACK_TO_PREVIOUS인 경우는 이미 처리됨)
                if (value < 0) {
                    if (allowZero) {
                        System.out.println("❌ 음수는 허용되지 않습니다. 0(뒤로가기) 또는 양수를 입력해주세요.");
                    } else {
                        System.out.println("❌ 음수는 허용되지 않습니다. 양수를 입력해주세요.");
                    }
                    continue;
                }
                
                // 6️⃣ 0 값 검증 (allowZero가 false이고 0이 아닌 BACK_TO_PREVIOUS가 아닌 경우)
                if (!allowZero && value == 0) {
                    System.out.printf("❌ 0은 허용되지 않습니다. %d~%d 사이의 양수를 입력해주세요.%n", min, max);
                    continue;
                }
                
                // 7️⃣ 범위 검증
                if (value >= min && value <= max) {
                    return value;
                }
                
                // 8️⃣ 범위 벗어남 상세 에러 메시지
                if (allowZero && min > 0) {
                    System.out.printf("❌ 입력값이 범위를 벗어났습니다. %d~%d 또는 0(뒤로가기)을 입력해주세요.%n", min, max);
                } else if (allowZero && min == 0) {
                    System.out.printf("❌ 입력값이 범위를 벗어났습니다. %d~%d 사이의 숫자를 입력해주세요.%n", min, max);
                } else {
                    System.out.printf("❌ 입력값이 범위를 벗어났습니다. %d~%d 사이의 숫자를 입력해주세요.%n", min, max);
                }
                
            } catch (NumberFormatException e) {
                // 9️⃣ 예외 처리 (정규식 검증을 통과했으므로 이론적으로 도달하지 않음)
                System.out.println("❌ 숫자 변환에 실패했습니다. 유효한 정수를 입력해주세요.");
            }
        }
    }

    /**
     * 거래소 메인화면 열기
     */
    public void open() throws IOException {
        initializeUserInfo();
        loadExchangeData();
        displayExchangeMenu();
    }

    /**
     * 사용자 정보 초기화
     */
    private void initializeUserInfo() {
        this.userId = MapService.userID;
        this.nickname = MapService.playerName;
    }

    /**
     * 고유한 거래 아이템 ID 생성
     * @return 고유 ID 문자열
     */
    public String generateUniqueId() {
        return this.userId + "_" + System.currentTimeMillis();
    }

    /**
     * 거래 데이터 로드
     */
    public void loadExchangeData() throws IOException {
        try (FileReader reader = new FileReader(FilePaths.EXCHANGE_DATA_PATH)) {
            exchangeItems = GSON.fromJson(reader, ExchangeItem[].class);
        }
    }

    /**
     * 거래 데이터 저장
     */
    public void saveExchangeData() throws IOException {
        try (FileWriter writer = new FileWriter(FilePaths.EXCHANGE_DATA_PATH)) {
            GSON.toJson(exchangeItems, ExchangeItem[].class, writer);
        }
    }

    /**
     * 거래소 메인 메뉴 표시 및 처리
     */
    public void displayExchangeMenu() {
        boolean continueMenu = true;

        while (continueMenu) {
            printExchangeMenuHeader();
            System.out.print("선택 >> ");

            int choice = getValidNumberInput(MENU_BUY_ITEM, MENU_EXIT, false);

            switch (choice) {
                case MENU_BUY_ITEM -> buyItem();
                case MENU_SELL_ITEM -> sellItem();
                case MENU_EXIT -> continueMenu = false;
            }
        }
    }

    /**
     * 거래소 메뉴 헤더 출력
     */
    private void printExchangeMenuHeader() {
        System.out.println("+=============================+");
        System.out.println("|           거래소             |");
        System.out.println("+=============================+");
        System.out.println("|                             |");
        System.out.println("|    1. 아이템 구매             |");
        System.out.println("|    2. 아이템 등록             |");
        System.out.println("|    3. 거래소 종료             |");
        System.out.println("|                             |");
        System.out.println("+=============================+");
    }

    /**
     * 아이템 목록 표시 및 구매 처리
     */
    public void displayItemList() throws IOException {
        System.out.println("+===========아이템 구매===========+");
        printItemsWithDisplayId();
        System.out.println("0. 정렬 필터링");
        System.out.print("구매할 아이템의 ID를 입력하세요 >> ");

        while (true) {
            int choice = getValidNumberInput();

            if (choice == BACK_TO_PREVIOUS) {
                sortItems();
                break;
            } else if (isValidItemChoice(choice)) {
                selectedItemIndex = choice - DISPLAY_ID_START;
                processBuyItem(exchangeItems[selectedItemIndex]);
                break;
            } else {
                System.out.println("존재하지 않는 인덱스 정보입니다.");
                System.out.print("다시 입력해주세요 >> ");
            }
        }
    }

    /**
     * 아이템 목록을 표시 ID와 함께 출력
     */
    private void printItemsWithDisplayId() {
        int displayId = DISPLAY_ID_START;
        for (ExchangeItem item : exchangeItems) {
            System.out.printf("%3d. %-15s x%-2d - %d골드 (%s)%n",
                    displayId++,
                    item.getName(),
                    item.getCount(),
                    item.getPrice(),
                    item.getNickname());
        }
    }

    /**
     * 선택한 아이템 번호가 유효한지 확인
     */
    private boolean isValidItemChoice(int choice) {
        int index = choice - DISPLAY_ID_START;
        return index >= 0 && index < exchangeItems.length;
    }

    /**
     * 아이템 정렬/필터링 메뉴
     */
    public void sortItems() {
        printSortMenuHeader();
        System.out.print("선택 >> ");

        while (true) {
            int choice = getValidNumberInput(1, 4, true);

            if (choice == BACK_TO_PREVIOUS) {
                return;
            }

            List<ExchangeItem> filteredItems = processFilterChoice(choice);
            if (filteredItems != null) {
                displayFilteredItems(filteredItems);
                break;
            }
        }
    }

    /**
     * 정렬/필터링 메뉴 헤더 출력
     */
    private void printSortMenuHeader() {
        System.out.println("+=============================+");
        System.out.println("|       정렬 / 필터링           |");
        System.out.println("+=============================+");
        System.out.println("|                             |");
        System.out.println("|    1. 가격 기준 오름차순       |");
        System.out.println("|    2. 가격 기준 내림차순       |");
        System.out.println("|    3. 특정 아이템만 보기       |");
        System.out.println("|    4. 유저 정보로 검색         |");
        System.out.println("|    0. 이전 화면으로 돌아가기    |");
        System.out.println("|                             |");
        System.out.println("+=============================+");
    }

    /**
     * 필터링 선택 처리
     */
    private List<ExchangeItem> processFilterChoice(int choice) {
        switch (choice) {
            case SORT_PRICE_ASC -> {
                return new ByPriceThenTimeAsc().apply(exchangeItems);
            }
            case SORT_PRICE_DESC -> {
                return new ByPriceThenTimeDesc().apply(exchangeItems);
            }
            case FILTER_BY_ITEM -> {
                return handleItemFiltering();
            }
            case FILTER_BY_USER -> {
                return handleUserFiltering();
            }
            default -> {
                System.out.println("올바른 번호를 선택하세요.");
                System.out.print("다시 입력해주세요 >> ");
                return null;
            }
        }
    }

    /**
     * 특정 아이템으로 필터링 처리
     */
    private List<ExchangeItem> handleItemFiltering() {
        List<ExchangeItem> sortedItems = new ByPriceThenTimeAsc().apply(exchangeItems);
        List<ExchangeItem> uniqueItems = getUniqueItemsByName(sortedItems);
        
        if (uniqueItems.isEmpty()) {
            System.out.println("❌ 등록된 아이템이 없습니다.");
            return null;
        }
        
        displayItemsWithIndex(uniqueItems);
        System.out.print("아이템 번호 선택 > ");
        
        int input = getValidNumberInput(1, uniqueItems.size(), false);
        String selectedItemName = uniqueItems.get(input - 1).getName();
        
        List<ExchangeItem> filteredItems = new IsMatchingItem(selectedItemName).apply(exchangeItems);
        
        if (filteredItems.isEmpty()) {
            System.out.println("❌ 해당 아이템을 찾을 수 없습니다.");
            return null;
        }
        
        return filteredItems;
    }

    /**
     * 아이템명 기준으로 중복 제거
     */
    private List<ExchangeItem> getUniqueItemsByName(List<ExchangeItem> items) {
        return items.stream()
                .collect(Collectors.toMap(
                        ExchangeItem::getName,
                        item -> item,
                        (existing, replacement) -> existing
                ))
                .values()
                .stream()
                .toList();
    }

    /**
     * 특정 유저로 필터링 처리
     */
    private List<ExchangeItem> handleUserFiltering() {
        System.out.print("유저 닉네임 입력 > ");
        String userName = SCANNER.nextLine().trim();
        
        if (userName.isEmpty()) {
            System.out.println("❌ 닉네임을 입력해주세요.");
            return null;
        }
        
        List<ExchangeItem> filteredItems = new IsMatchingNickname(userName).apply(exchangeItems);
        
        if (filteredItems.isEmpty()) {
            System.out.printf("❌ '%s' 닉네임을 가진 유저의 등록 아이템을 찾을 수 없습니다.%n", userName);
            return null;
        }
        
        return filteredItems;
    }

    /**
     * 필터링된 아이템 목록 표시
     */
    private void displayFilteredItems(List<ExchangeItem> items) {
        System.out.printf("%n✅ 총 %d개의 아이템을 찾았습니다:%n", items.size());
        displayItemsWithIndex(items);
        System.out.println();
    }

    /**
     * 아이템 목록을 인덱스와 함께 표시
     */
    private void displayItemsWithIndex(List<ExchangeItem> items) {
        Consumer<ExchangeItem> printItem = item -> 
            System.out.printf("%3d. %s%n", items.indexOf(item) + 1, item);
        items.forEach(printItem);
    }

    /**
     * 구매 처리
     */
    public void buyItem() {
        try {
            displayItemList();
        } catch (IOException e) {
            System.out.println("❌ 데이터 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 구매 처리 로직
     */
    private void processBuyItem(ExchangeItem selectedItem) throws IOException {
        loadPlayersData();
        Player currentPlayer = getCurrentPlayer();
        
        if (currentPlayer == null) {
            System.out.println("❌ 플레이어 정보를 찾을 수 없습니다.");
            return;
        }

        // 자신이 판매한 아이템인지 확인
        if (selectedItem.getUserId().equals(userId)) {
            System.out.println("❌ 자신이 판매한 아이템은 구매할 수 없습니다.");
            return;
        }

        System.out.printf("%n선택한 아이템: %s (판매자: %s)%n", 
                selectedItem.getName(), selectedItem.getNickname());
        System.out.printf("개당 가격: %d골드, 등록 수량: %d개%n", 
                selectedItem.getPrice(), selectedItem.getCount());
        System.out.printf("현재 보유 골드: %d골드%n", currentPlayer.getGold());
        
        // 구매할 개수 입력
        System.out.print("구매할 개수를 입력하세요 >> ");
        int buyCount = getValidNumberInput(1, selectedItem.getCount(), false);
        
        // 총 가격 계산
        int totalPrice = selectedItem.getPrice() * buyCount;
        
        // 골드 충분성 검사
        if (currentPlayer.getGold() < totalPrice) {
            System.out.printf("❌ 골드가 부족합니다! 필요: %d골드, 보유: %d골드%n", 
                    totalPrice, currentPlayer.getGold());
            return;
        }
        
        // 구매 확인
        System.out.printf("%n=== 구매 정보 확인 ===%n");
        System.out.printf("아이템: %s%n", selectedItem.getName());
        System.out.printf("구매 개수: %d개%n", buyCount);
        System.out.printf("개당 가격: %d골드%n", selectedItem.getPrice());
        System.out.printf("총 가격: %d골드%n", totalPrice);
        System.out.printf("구매 후 잔여 골드: %d골드%n", currentPlayer.getGold() - totalPrice);
        System.out.print("정말 구매하시겠습니까? (1: 예, 0: 아니오) >> ");
        
        int confirm = getValidNumberInput(0, 1, false);
        
        if (confirm == 1) {
            ItemName itemName;
            try {
                itemName = ItemName.valueOf(selectedItem.getName());
            } catch (IllegalArgumentException e) {
                System.out.println("❌ 알 수 없는 아이템 이름입니다: " + selectedItem.getName());
                return;
            }

            boolean added = MapService.tryAddItemToInventory(currentPlayer, itemName, buyCount);
            if (added) {
                currentPlayer.setGold(currentPlayer.getGold() - totalPrice);
                selectedItem.setCount(selectedItem.getCount() - buyCount);
                savePlayersData(); // 필요시 파일 저장
                System.out.printf("✅ %s %d개를 성공적으로 구매했습니다!%n",
                        selectedItem.getName(), buyCount);
            } else {
                System.out.println("❌ 인벤토리에 추가하지 못했습니다.(무게 초과)");
            }
        } else {
            System.out.println("❌ 구매가 취소되었습니다.");
        }
    }

    /**
     * 아이템 구매 실행
     */
    private void executeItemPurchase(ExchangeItem selectedItem, Player currentPlayer, 
                                   int buyCount, int totalPrice) throws IOException {
        // 1. 플레이어 골드 차감
        currentPlayer.setGold(currentPlayer.getGold() - totalPrice);
        
        // 2. 플레이어 인벤토리에 아이템 추가
        addItemToPlayerInventory(currentPlayer, selectedItem.getName(), buyCount);
        
        // 3. 거래소에서 아이템 제거/감소
        updateExchangeItemAfterPurchase(selectedItem, buyCount);
        
        // 4. 데이터 저장
        savePlayersData();
        saveExchangeData();
    }

    /**
     * 플레이어 인벤토리에 아이템 추가
     */
    private void addItemToPlayerInventory(Player player, String itemName, int addCount) {
        for (Item item : player.getItems()) {
            if (item.getName().toString().equals(itemName)) {
                item.setCount(item.getCount() + addCount);
                return;
            }
        }
        // 만약 해당 아이템이 인벤토리에 없다면 (일반적으로는 모든 아이템이 0개로라도 있음)
        System.out.printf("⚠️ 경고: 인벤토리에 %s 항목을 찾을 수 없습니다.%n", itemName);
    }

    /**
     * 거래소에서 아이템 구매 후 수량 업데이트
     */
    private void updateExchangeItemAfterPurchase(ExchangeItem purchasedItem, int buyCount) {
        List<ExchangeItem> itemList = new ArrayList<>(Arrays.asList(exchangeItems));
        
        if (purchasedItem.getCount() == buyCount) {
            // 전체 수량을 구매한 경우 - 거래소에서 제거
            itemList.removeIf(item -> item.getId().equals(purchasedItem.getId()));
        } else {
            // 일부만 구매한 경우 - 수량 감소
            for (ExchangeItem item : itemList) {
                if (item.getId().equals(purchasedItem.getId())) {
                    item.setCount(item.getCount() - buyCount);
                    break;
                }
            }
        }
        
        // 배열로 다시 변환
        exchangeItems = itemList.toArray(new ExchangeItem[0]);
    }

    /**
     * 판매 등록
     */
    public void sellItem() {
        try {
            displaySellableItemList();
        } catch (IOException e) {
            System.out.println("❌ 데이터 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 플레이어 데이터 로드
     */
    private void loadPlayersData() throws IOException {
        try (FileReader reader = new FileReader(FilePaths.PLAYER_DATA_PATH)) {
            Type type = new TypeToken<Map<String, Player>>(){}.getType();
            playersData = GSON.fromJson(reader, type);
        }
    }

    /**
     * 플레이어 데이터 저장
     */
    private void savePlayersData() throws IOException {
        try (FileWriter writer = new FileWriter(FilePaths.PLAYER_DATA_PATH)) {
            GSON.toJson(playersData, writer);
        }
    }

    /**
     * 현재 플레이어 정보 가져오기
     */
    private Player getCurrentPlayer() {
        return playersData.get(userId);
    }

    /**
     * 판매 가능한 아이템 목록 표시 및 등록 처리
     */
    public void displaySellableItemList() throws IOException {
        loadPlayersData();
        Player currentPlayer = getCurrentPlayer();
        
        if (currentPlayer == null) {
            System.out.println("❌ 플레이어 정보를 찾을 수 없습니다.");
            return;
        }

        List<Item> sellableItems = getSellableItems(currentPlayer);
        
        if (sellableItems.isEmpty()) {
            System.out.println("❌ 판매할 수 있는 아이템이 없습니다.");
            return;
        }

        System.out.println("+===========아이템 판매===========+");
        printSellableItemsWithDisplayId(sellableItems);
        System.out.println("0. 이전 화면으로 돌아가기");
        System.out.print("판매할 아이템의 ID를 입력하세요 >> ");

        while (true) {
            int choice = getValidNumberInput(1, sellableItems.size(), true);

            if (choice == BACK_TO_PREVIOUS) {
                return;
            } else {
                selectedItemIndex = choice - 1;
                processSellItem(sellableItems.get(selectedItemIndex), currentPlayer);
                break;
            }
        }
    }

    /**
     * 판매 가능한 아이템 목록 가져오기 (개수가 1개 이상인 아이템들)
     */
    private List<Item> getSellableItems(Player player) {
        return Arrays.stream(player.getItems())
                .filter(item -> item.getCount() > 0)
                .toList();
    }

    /**
     * 판매 가능한 아이템 목록을 표시 ID와 함께 출력
     */
    private void printSellableItemsWithDisplayId(List<Item> items) {
        int displayId = 1;
        for (Item item : items) {
            System.out.printf("%3d. %-15s x%-2d개%n",
                    displayId++,
                    item.getName(),
                    item.getCount());
        }
    }

    /**
     * 아이템 판매 처리
     */
    private void processSellItem(Item selectedItem, Player currentPlayer) throws IOException {
        System.out.printf("%n선택한 아이템: %s (보유: %d개)%n", 
                selectedItem.getName(), selectedItem.getCount());
        
        // 판매할 개수 입력
        System.out.print("판매할 개수를 입력하세요 >> ");
        int sellCount = getValidNumberInput(1, selectedItem.getCount(), false);
        
        // 판매 가격 입력
        System.out.print("개당 판매 가격을 입력하세요 (골드) >> ");
        int sellPrice = getValidNumberInput(1, Integer.MAX_VALUE, false);
        
        // 확인 메시지
        int totalPrice = sellCount * sellPrice;
        System.out.printf("%n=== 판매 정보 확인 ===%n");
        System.out.printf("아이템: %s%n", selectedItem.getName());
        System.out.printf("판매 개수: %d개%n", sellCount);
        System.out.printf("개당 가격: %d골드%n", sellPrice);
        System.out.printf("총 예상 수익: %d골드%n", totalPrice);
        System.out.print("정말 판매하시겠습니까? (1: 예, 0: 아니오) >> ");
        
        int confirm = getValidNumberInput(0, 1, false);
        
        if (confirm == 1) {
            registerItemToExchange(selectedItem.getName().toString(), sellCount, sellPrice);
            updatePlayerInventory(currentPlayer, selectedItem, sellCount);
            System.out.printf("✅ %s %d개가 거래소에 등록되었습니다!%n", 
                    selectedItem.getName(), sellCount);
        } else {
            System.out.println("❌ 판매가 취소되었습니다.");
        }
    }

    /**
     * 거래소에 아이템 등록
     */
    private void registerItemToExchange(String itemName, int count, int price) throws IOException {
        // 기존 거래 아이템들을 리스트로 변환
        List<ExchangeItem> itemList = new ArrayList<>(Arrays.asList(exchangeItems));
        
        // 새 거래 아이템 생성 (타임스탬프를 초 단위로 변환)
        ExchangeItem newItem = new ExchangeItem(
                generateUniqueId(),
                itemName,
                count,
                price,
                userId,
                nickname,
                (int) (System.currentTimeMillis() / 1000)
        );
        
        // 새 아이템 추가
        itemList.add(newItem);
        
        // 배열로 다시 변환
        exchangeItems = itemList.toArray(new ExchangeItem[0]);
        
        // 거래소 데이터 저장
        saveExchangeData();
    }

    /**
     * 플레이어 인벤토리 업데이트 (아이템 개수 감소)
     */
    private void updatePlayerInventory(Player currentPlayer, Item soldItem, int soldCount) throws IOException {
        // 아이템 개수 감소
        soldItem.setCount(soldItem.getCount() - soldCount);
        
        // 플레이어 데이터 저장
        savePlayersData();
    }
}