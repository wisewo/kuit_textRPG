package services;

import models.Item;
import models.Player;
import models.ItemDetail;
import models.ItemName;
import models.PlayerPosition;
import utils.Util;

import java.io.*;
import java.util.*;

public class MapService {
    static Scanner scanner = new Scanner(System.in);
    static Random rand = new Random();
    static ExchangeService exchangeService = new ExchangeService();

    static final int ROW = 10;
    static final int COL = 10;
    static String[][][] maps = new String[3][ROW][COL];
    static String userID;

    static int currentMap = 0;
    static int playerX = 0, playerY = 0;
    static int startX = 0;


    static boolean is_win = false;
    static boolean hasKey = false;
    static boolean inBattle = false;
    public static boolean bombUsed = false;
    public static boolean itemUsed = false;
    static String playerName;
    static int playerHP = 100;
    static int playerHPMAX = 100;
    static int playerAttack = 30;
    static List<Item> playerItems = new ArrayList<>();


    public static void MapMain(String id, String nickname, Player player) throws IOException {
        userID = id;
        playerName = nickname;
        player.setCurrentHp(playerHPMAX);
        JsonService.saveById(userID, player, Player.class);
        playerHP = player.getCurrentHp();
        playerHPMAX = player.getBaseMaxHp();
        playerAttack = player.getBaseStr();
        playerItems = new ArrayList<>(Arrays.asList(player.getItems()));


        File mapFile = new File("maps/" + userID + ".txt");
        if (!mapFile.exists()) {
            System.out.println("신규 유저입니다. 맵을 새로 생성합니다.");
            generateMaps();
            saveMaps(userID);
        }

        loadMapsFromFile(userID);
        if (!validateMaps()) {
            System.out.println("맵에 오류가 있어 종료합니다.");
            System.exit(0); // 또는 기본 맵 로드
        }
        //위치 범위 유효성 검사
        if ((player.getPosition().getMapId() >= 0 && player.getPosition().getMapId() < 3) && (player.getPosition().getX() >= 0 && player.getPosition().getX() < ROW) && (player.getPosition().getY() >= 0 && player.getPosition().getY() < COL)) {
            //위치가 길인지 검사
            if (maps[player.getPosition().getMapId()][player.getPosition().getX()][player.getPosition().getY()].equals("□")) { //저장된 마지막 위치가 길이라면
                currentMap = player.getPosition().getMapId();
                playerX = player.getPosition().getX();
                playerY = player.getPosition().getY();
            }
        }
        else { //유효하지 않은 경우 시작 위치로 이동(사실 validateMaps에서 이미 시작위치로 초기화 됨)
            System.out.printf("경고: 저장된 위치(mapId=%d, x=%d, y=%d)가 유효하지 않아 시작 위치로 이동합니다.%n", player.getPosition().getMapId(), player.getPosition().getX(), player.getPosition().getY());
        }
        Util.cleanConsole();
        System.out.println("맵 검사 통과. 게임을 시작합니다.");
        pause();
        gameLoop(userID, player, playerItems);
    }

    public static void gameLoop(String Id, Player player, List<Item> items) throws IOException {
        while (true) {
            savePlayerState(Id, player);
            saveMaps(userID);
            clearScreen();
            printMap(maps[currentMap], player, items);  // player 전달
            System.out.println("[W]위  [A]왼쪽  [S]아래  [D]오른쪽  [I]인벤토리 [O]거래소");
            System.out.print("선택>> ");
            String move = scanner.nextLine().toUpperCase();

            // 몬스터 다 잡았는데 열쇠 안 떴을 때 구제 몬스터 생성
            int Mnum = 0;
            for (int i = 0; i < ROW; i++) {
                for (int j = 0; j < COL; j++) {
                    if (maps[currentMap][i][j].equals("♤")) Mnum++;
                }
            }
            if (Mnum == 0 && !hasKey) {
                maps[1][4][4] = "♤";
            }

            int newX = playerX;
            int newY = playerY;
            switch (move) {
                case "W":
                    if (playerX <= 0) {
                        System.out.println("맵 밖으로는 이동할 수 없습니다!");
                        pause();
                        continue;
                    }
                    newX = playerX - 1;
                    break;
                case "S":
                    if (playerX >= ROW - 1) {
                        System.out.println("맵 밖으로는 이동할 수 없습니다!");
                        pause();
                        continue;
                    }
                    newX = playerX + 1;
                    break;
                case "A":
                    if (playerY == 0 && currentMap > 0) {
                        System.out.println("이전 맵으로 이동합니다!");
                        currentMap--;
                        newY = COL - 1;
                        pause();
                    } else if (playerY == 0) {
                        System.out.println("맵 밖으로는 이동할 수 없습니다!");
                        pause();
                    } else {
                        newY = playerY - 1;
                    }
                    break;
                case "D":
                    if (playerY == COL - 1 && currentMap < 2) {
                        System.out.println("다음 맵으로 이동합니다!");
                        currentMap++;
                        newY = 0;
                        pause();
                    } else if (playerY == COL - 1) {
                        System.out.println("맵 밖으로는 이동할 수 없습니다!");
                        pause();
                    } else {
                        newY = playerY + 1;
                    }
                    break;
                case "I":
                    showInventory(player);
                    break;
                case "O":
                    exchangeService.open();
                    break;
                default:
                    System.out.println("W, A, S, D 중 하나를 입력하세요");
                    pause();
                    break;
            }

            String nextTile = maps[currentMap][newX][newY];
            if (nextTile.equals("□") || nextTile.equals("♤") || nextTile.equals("♧") || nextTile.equals("@") || nextTile.equals("▣") || nextTile.equals("◈")) {
                playerX = newX;
                playerY = newY;
            } else {
                System.out.println("길 위로만 이동할 수 있습니다!");
                pause();
                continue;
            }

            String currentTile = maps[currentMap][playerX][playerY];
            if (currentTile.equals("♤")) { //몬스터
                System.out.println("몬스터를 만났습니다!");
                pause();
                inBattle = true;
                BattleService.fight("normal", player, items);
                inBattle = false;
                if (is_win) {
                    maps[currentMap][playerX][playerY] = "□";
                    checkAndRegenerateAllMonsters();
                }
            } else if (currentTile.equals("♧")) { //보스 몬스터
                if (BattleService.playerHasBossKey(items)) {
                    inBattle = true;
                    BattleService.fight("boss", player, items);
                    inBattle = false;
                    if (is_win) {
                        System.out.println("보스를 처치했습니다! 승리!");
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        regenerateMonsters();
                        savePlayerState(Id, player);
                        saveMaps(userID);
                        break;
                    }
                } else {
                    System.out.println("열쇠가 필요합니다!");
                    pause();
                }
            } else if (currentTile.equals("◈")) {
                new ShopService().open(player);
            } else if (currentTile.equals("▣")) {
                openChest(player, items);
            }
        }

        System.out.println("게임 종료!");
    }


    public static void showInventory(Player player) {
        List<Item> inventory = new ArrayList<>();
        for (Item item : player.getItems()) {
            if (item.getCount() > 0) {
                inventory.add(item);
            }
        }

        if (inventory.isEmpty()) {
            System.out.println("| 보유 중인 아이템이 없습니다. |");
            System.out.println("\n계속하려면 Enter를 누르세요...");
            scanner.nextLine();
            return;
        }

        int playerHP = player.getCurrentHp();
        int playerHPMAX = player.getEffectiveMaxHp();

        while (true) {
            System.out.println("|=== 보유 아이템 목록 ===|");
            System.out.printf("|=== 무게 ( %d / %d ) ===|\n", player.getCurrentCarryWeight(), player.MAX_CARRY_WEIGHT);
            for (int i = 0; i < inventory.size(); i++) {
                Item item = inventory.get(i);
                System.out.println((i + 1) + ". " + item);
            }
            System.out.println("0. 인벤토리 닫기");

            System.out.print("선택할 아이템의 번호: ");
            String line = scanner.nextLine();
            int choice;
            try {
                choice = Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("잘못된 입력입니다. 숫자를 입력해주세요.");
                continue;
            }

            if (choice == 0) return;
            if (choice < 1 || choice > inventory.size()) {
                System.out.println("메뉴에 없는 번호입니다.");
                continue;
            }

            Item selected = inventory.get(choice - 1);
            String key = selected.getName().toString();
            boolean isUsable = key.equals("hpPotion") || key.equals("largeHpPotion") || key.equals("bomb");

            while (true) {
                if (isUsable) {
                    System.out.println("1. 사용");
                    System.out.println("2. 버리기");
                    System.out.println("3. 선택 취소");
                    System.out.print("선택: ");
                    line = scanner.nextLine();

                    int action;
                    try {
                        action = Integer.parseInt(line);
                    } catch (NumberFormatException e) {
                        System.out.println("잘못된 입력입니다.");
                        continue;
                    }

                    if (action == 1) {
                        switch (key) {
                            case "hpPotion" -> {
                                int heal = Math.min(playerHPMAX - playerHP, 30);
                                playerHP += heal;
                                player.setCurrentHp(playerHP);
                                System.out.printf("체력을 %d만큼 회복했습니다 (%d/%d)%n", heal, playerHP, playerHPMAX);
                                selected.setCount(selected.getCount() - 1);
                                itemUsed = true;
                            }
                            case "largeHpPotion" -> {
                                int heal = Math.min(playerHPMAX - playerHP, 50);
                                playerHP += heal;
                                player.setCurrentHp(playerHP);
                                System.out.printf("체력을 %d만큼 회복했습니다 (%d/%d)%n", heal, playerHP, playerHPMAX);
                                selected.setCount(selected.getCount() - 1);
                                itemUsed = true;
                            }
                            case "bomb" -> {
                                if(inBattle){
                                    System.out.println("40의 피해를 입혔습니다.   ");
                                    System.out.println();
                                    System.out.println();
                                    bombUsed = true;
                                    selected.setCount(selected.getCount() - 1);
                                    try {
                                        Thread.sleep(2000);
                                    } catch (InterruptedException e) {
                                        throw new RuntimeException(e);
                                    }
                                    itemUsed = true;
                                }else{      //전투 중이 아닐 때의 폭탄 사용
                                    boolean exploded = false;
                                    int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
                                    for (int[] d : dirs) {
                                        int nx = playerX + d[0], ny = playerY + d[1];
                                        if (nx >= 0 && nx < ROW && ny >= 0 && ny < COL
                                                && maps[currentMap][nx][ny].equals("▨")) {
                                            maps[currentMap][nx][ny] = "□";
                                            exploded = true;
                                        }
                                    }
                                    if (exploded) {
                                        System.out.println("폭탄이 터져 주변 가벽이 제거되었습니다!");
                                        selected.setCount(selected.getCount() - 1);
                                    } else {
                                        System.out.println("플레이어 주변 상하좌우 1칸 이내에 제거할 가벽이 없습니다.");
                                    }
                                }

                            }
                        }
                        if(!inBattle){
                            pause();
                        }
                        return;
                    } else if (action == 2) {
                        selected.setCount(selected.getCount() - 1);
                        printItemDropMessage(key);
                        pause();
                        return;
                    } else if (action == 3) {
                        break;
                    } else {
                        System.out.println("메뉴에 없는 번호입니다.");
                    }
                } else {
                    System.out.println("1. 버리기");
                    System.out.println("2. 선택 취소");
                    System.out.print("선택: ");
                    line = scanner.nextLine();

                    int action;
                    try {
                        action = Integer.parseInt(line);
                    } catch (NumberFormatException e) {
                        System.out.println("잘못된 입력입니다.");
                        continue;
                    }

                    if (action == 1) {
                        selected.setCount(selected.getCount() - 1);
                        printItemDropMessage(key);
                        pause();
                        return;
                    } else if (action == 2) {
                        break;
                    } else {
                        System.out.println("메뉴에 없는 번호입니다.");
                    }
                }
            }
        }
    }

    private static void printItemDropMessage(String key) {
        String displayName = switch (key) {
            case "hpPotion"      -> "체력 포션";
            case "largeHpPotion" -> "대용량 체력 포션";
            case "bomb"          -> "폭탄";
            case "dagger"        -> "단검";
            case "longsword"     -> "장검";
            case "shield"        -> "방패";
            case "armor"         -> "갑옷";
            default              -> key;
        };
        System.out.printf("%s을(를) 버렸습니다.%n", displayName);
    }




    public static boolean validateMaps() { //맵 파일 무결성 검사
        Set<String> validTiles = new HashSet<>(Arrays.asList("■", "▨","▣", "◈", "□", "@", "♤", "♧", " ", "\n"));
        int totalPaths = 0;
        boolean startFound = false;
        int startMap = -1, startX = -1, startY = -1;
        int bossCount = 0;

        // 문자 유효성 & 시작 위치, 보스 위치 확인
        for (int m = 0; m < 3; m++) {
            for (int i = 0; i < ROW; i++) {
                for (int j = 0; j < COL; j++) {
                    String tile = maps[m][i][j];
                    if (!validTiles.contains(tile)) {
                        System.out.println("오류: 허용되지 않은 문자 '" + tile + "'가 발견되었습니다. (" + m + "맵, " + i + "행 " + j + "열)");
                        return false;
                    }
                    if (tile.equals("□") || tile.equals("@") || tile.equals("♧") || tile.equals("▨") || tile.equals("▣") || tile.equals("◈")) totalPaths++;
                    if (tile.equals("@")) {
                        if(m != 0) {
                            System.out.println("오류: 두 번째 이후의 맵에 시작 위치가 존재합니다.");
                            return false;
                        }
                        if(j != 0) {
                            System.out.println("오류: 첫 열이 아닌 열에 시작 위치가 존재합니다.");
                            return false;
                        }
                        if (startFound) {
                            System.out.println("오류: 시작 위치(@)가 2개 이상 존재합니다.");
                            return false;
                        }
                        startFound = true;
                        startMap = m;
                        startX = i;
                        startY = j;
                        //System.out.println(startMap+ " "+ startX + " "+ startY); //테스트용 출력
                    }
                    if (tile.equals("♧") && m == 2) {
                        bossCount++;
                    }
                }
            }

            // 각 맵의 몬스터 개수 검사
            int monsterCount = 0;
            for (int i = 0; i < ROW; i++) {
                for (int j = 0; j < COL; j++) {
                    if (maps[m][i][j].equals("♤")) {
                        monsterCount++;
                    }
                }
            }
            if (monsterCount < 2) {
                System.out.printf("오류: %d번째 맵에 몬스터가 %d마리밖에 없습니다. 최소 2마리 이상이어야 합니다.%n", m + 1, monsterCount);
                return false;
            }
        }

        if (!startFound) {
            System.out.println("오류: 시작 위치(@)가 존재하지 않습니다.");
            return false;
        }
        if (bossCount != 1) {
            System.out.println("오류: 세 번째 맵에 보스(♧)가 정확히 1개 존재해야 합니다. 현재 개수: " + bossCount);
            return false;
        }

        // 모든 몬스터가 길(□) 또는 시작 위치(@)에 인접해 있는지 검사
        int[][] directionsForMonster = {{-1,0},{1,0},{0,-1},{0,1}};

        for (int m = 0; m < 3; m++) {
            for (int i = 0; i < ROW; i++) {
                for (int j = 0; j < COL; j++) {
                    if (maps[m][i][j].equals("♤")) {
                        boolean adjacentToPath = false;

                        for (int[] d : directionsForMonster) {
                            int ni = i + d[0];
                            int nj = j + d[1];
                            if (ni >= 0 && ni < ROW && nj >= 0 && nj < COL) {
                                String neighbor = maps[m][ni][nj];
                                if (neighbor.equals("□") || neighbor.equals("@")) {
                                    adjacentToPath = true;
                                    break;
                                }
                            }
                        }

                        if (!adjacentToPath) {
                            System.out.printf("오류: (%d맵, %d행 %d열)의 몬스터가 길과 연결되어 있지 않습니다.%n", m, i, j);
                            return false;
                        }
                    }
                }
            }
        }

        // BFS로 길 연결 여부 검사
        boolean[][][][] visited = new boolean[3][ROW][COL][1]; // [맵][x][y][dummy]
        Queue<int[]> queue = new LinkedList<>();
        queue.offer(new int[]{startMap, startX, startY});
        visited[startMap][startX][startY][0] = true;

        int connectedPaths = 1;
        int[][] directions = {{-1,0},{1,0},{0,-1},{0,1}};

        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int m = pos[0], x = pos[1], y = pos[2];

            for (int[] d : directions) {
                int nx = x + d[0];
                int ny = y + d[1];
                int nm = m;

                // 맵 간 좌우 이동 처리
                if (ny < 0) {
                    if (m == 0) continue;
                    nm = m - 1;
                    ny = COL - 1;
                } else if (ny >= COL) {
                    if (m == 2) continue;
                    nm = m + 1;
                    ny = 0;
                }

                if (nx < 0 || nx >= ROW || ny < 0 || ny >= COL) continue;
                if (visited[nm][nx][ny][0]) continue;

                String tile = maps[nm][nx][ny];
                if (tile.equals("□") || tile.equals("@") || tile.equals("♧") || tile.equals("▨") || tile.equals("▣") || tile.equals("◈")) {
                    visited[nm][nx][ny][0] = true;
                    queue.offer(new int[]{nm, nx, ny});
                    connectedPaths++;
                }
            }
        }

        if (connectedPaths < totalPaths) {
            System.out.println("오류: 모든 길(□)이 연결되어 있지 않습니다. 연결된 길: " + connectedPaths + " / 전체 길: " + totalPaths);
            return false;
        }

        //첫 열과 마지막 열에 길이 2개 이상 있는지 검사
        for(int m = 0; m < 3; m++) {
            int firstColPath = 0;
            int lastColPath = 0;
            for(int i = 0; i < ROW; i++) {
                if(maps[m][i][0].equals("□") || maps[m][i][0].equals("@") || maps[m][i][0].equals("♧")|| maps[m][i][0].equals("▨") || maps[m][i][0].equals("▣") || maps[m][i][0].equals("◈")) {
                    firstColPath++;
                }
                if(maps[m][i][COL-1].equals("□") || maps[m][i][COL-1].equals("@") || maps[m][i][COL-1].equals("♧") || maps[m][i][COL-1].equals("▨") || maps[m][i][COL-1].equals("▣") || maps[m][i][COL-1].equals("◈")) {
                    lastColPath++;
                }
            }
            if(firstColPath > 1) {
                System.out.println((m+1)+"번째 맵의 첫 열에 이동할 수 있는 칸이 2개 이상 존재합니다.");
                return false;
            }
            if(lastColPath > 1) {
                System.out.println((m+1)+"번째 맵의 마지막 열에 이동할 수 있는 칸이 2개 이상 존재합니다.");
                return false;
            }
        }

        currentMap = startMap;
        playerX = startX;
        playerY = startY;
        System.out.println("검사 완료");
        return true;
    }


    public static void printMap(String[][] map, Player player, List<Item> items) {
        // 플레이어 상태 정보 출력
        System.out.println("┌─────────────────────────────┐");
        System.out.printf("│  닉네임   : %-16s│%n", playerName);
        System.out.printf("│  현재 체력: %3d / %3d         │%n", player.getCurrentHp(), player.getEffectiveMaxHp());
        System.out.printf("│  공격력   : %-20d│%n", player.getEffectiveStr());
        System.out.printf("│  골드     : %-20d│%n", player.getGold());
        System.out.printf("│  열쇠: %-13s│\n", BattleService.playerHasBossKey(items) ? "보유" : "없음");
        System.out.println("└─────────────────────────────┘");


        for (int i = 0; i < map.length; i++) {
            for (int j = 0; j < map[i].length; j++) {
                if (i == playerX && j == playerY) {
                    System.out.print("★ ");
                } else {
                    System.out.print(map[i][j] + " ");
                }
            }
            System.out.println();
        }
    }


    public static void loadMapsFromFile(String userID) {
        String mapPath = "maps/" + userID + ".txt";
        try (BufferedReader reader = new BufferedReader(new FileReader(mapPath))) {
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 10; j++) {
                    String line = reader.readLine();
                    if (line == null) {
                        System.out.println("맵 파일 형식 오류: 줄 수가 부족합니다.");
                        System.exit(0);
                    }
                    String[] tiles = line.trim().split("\\s+");
                    if (tiles.length != 10) {
                        System.out.println("맵 파일 형식 오류: 열의 개수가 10이 아닙니다.");
                        System.exit(0);
                    }
                    maps[i][j] = tiles;
                }

                // 빈 줄 스킵
                String skip = reader.readLine();
                if (i < 2 && (skip == null || !skip.trim().isEmpty())) {
                    System.out.println("맵 파일 형식 오류: 맵 사이 빈 줄이 없습니다.");
                    System.exit(0);
                }
            }

        } catch (IOException e) {
            System.out.println("맵 파일을 찾을 수 없어 새로 생성합니다.");
            generateMaps();
            saveMaps(userID);
        }
    }

    public static void generateMaps() {
        System.out.println("맵 생성중...");
        int lastX = 0;
        for (int m = 0; m < 3; m++) {
            for (int i = 0; i < ROW; i++) {
                for (int j = 0; j < COL; j++) {
                    maps[m][i][j] = "■";
                }
            }
        }

        int is_road_exist = 0;
        for (int m = 0; m < 3; m++) {
            //System.out.println((m + 1) + "번째 맵 생성중");
            for (int j = 0; j < COL; j++) {
                is_road_exist = 0;
                //System.out.println((j + 1) + "번째 열 생성중");

                if (j == 0) {
                    if (m == 0) {
                        while (is_road_exist == 0) {
                            for (int i = 0; i < ROW; i++) {
                                if (rand.nextInt(10) == 0) {
                                    maps[m][i][j] = "@";
                                    is_road_exist++;
                                    startX = i;
                                    playerX = i;
                                    break;
                                }
                            }
                        }
                    } else {
                        maps[m][lastX][j] = "□";
                    }
                } else if (j < COL - 1) {
                    while (is_road_exist == 0) {
                        for (int i = 0; i < ROW; i++) {
                            if ((maps[m][i][j - 1].equals("□") || maps[m][i][j - 1].equals("@")) && rand.nextInt(10) == 0) {
                                maps[m][i][j] = "□";
                                if (rand.nextInt(3) == 0)
                                    is_road_exist++;
                                break;
                            }
                        }
                    }
                    for (int k = 0; k < 3; k++) {
                        for (int i = 0; i < ROW; i++) {
                            if (maps[m][i][j].equals("□")) {
                                if (rand.nextInt(5) == 0 && i > 0) {
                                    maps[m][i - 1][j] = "□";
                                }
                                if (rand.nextInt(5) == 0 && i < ROW - 1) {
                                    maps[m][i + 1][j] = "□";
                                }
                            }
                        }
                    }
                } else {
                    if (m < 2) {
                        while (is_road_exist == 0) {
                            for (int i = 0; i < ROW; i++) {
                                if (maps[m][i][j - 1].equals("□") && rand.nextInt(10) == 0) {
                                    maps[m][i][j] = "□";
                                    is_road_exist++;
                                    lastX = i;
                                    break;
                                }
                            }
                        }
                    } else {
                        while (is_road_exist == 0) {
                            for (int i = 0; i < ROW; i++) {
                                if (maps[m][i][j - 1].equals("□") && rand.nextInt(10) == 0) {
                                    maps[m][i][j] = "♧";
                                    is_road_exist++;
                                    break;
                                }
                            }
                        }
                    }
                }
            }


            // 몬스터 랜덤 생성
            int monsterCount = 0;

            for (int i = 1; i < ROW - 1; i++) {
                for (int j = 1; j < COL - 1; j++) {
                    if (monsterCount >= 10) break; // 최대 10마리까지만

                    if (maps[m][i][j].equals("□")) {
                        // 상하좌우 벽 확인
                        if (maps[m][i - 1][j].equals("■") && rand.nextInt(10) == 0) {
                            maps[m][i - 1][j] = "♤";
                            monsterCount++;
                        }
                        if (monsterCount >= 10) break;

                        if (maps[m][i + 1][j].equals("■") && rand.nextInt(10) == 0) {
                            maps[m][i + 1][j] = "♤";
                            monsterCount++;
                        }
                        if (monsterCount >= 10) break;

                        if (maps[m][i][j - 1].equals("■") && rand.nextInt(10) == 0 && j - 1 > 0) {
                            maps[m][i][j - 1] = "♤";
                            monsterCount++;
                        }
                        if (monsterCount >= 10) break;

                        if (maps[m][i][j + 1].equals("■") && rand.nextInt(10) == 0 && j + 1 < COL - 1) {
                            maps[m][i][j + 1] = "♤";
                            monsterCount++;
                        }
                    }
                }
            }

// 몬스터가 2마리 미만일 경우 강제 생성
            while (monsterCount < 2) {
                for (int i = 1; i < ROW - 1; i++) {
                    for (int j = 1; j < COL - 1; j++) {
                        if (maps[m][i][j].equals("□")) {

                            // 상하좌우 벽 중에서 하나 선택해 확률적으로 몬스터 생성
                            int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
                            for (int[] d : directions) {
                                int ni = i + d[0];
                                int nj = j + d[1];
                                if (maps[m][ni][nj].equals("■") && rand.nextInt(2) == 0) {
                                    maps[m][ni][nj] = "♤";
                                    monsterCount++;
                                    break;
                                }
                            }
                        }
                        if (monsterCount >= 2) break;
                    }
                    if (monsterCount >= 2) break;
                }
            }
        }

        //상점 생성
        {
            int m = 1;              // 두 번째 맵
            int storePlaced = 0;

            // 몬스터 생성처럼 확률 루프 돌리기
            while (storePlaced < 1) {
                for (int i = 1; i < ROW - 1 && storePlaced < 1; i++) {
                    for (int j = 1; j < COL - 1 && storePlaced < 1; j++) {
                        if (maps[m][i][j].equals("■")) {

                            boolean adjacentPath =
                                    maps[m][i-1][j].equals("□") ||
                                            maps[m][i+1][j].equals("□") ||
                                            maps[m][i][j-1].equals("□") ||
                                            maps[m][i][j+1].equals("□");
                            if (adjacentPath && rand.nextInt(10) == 0) {
                                maps[m][i][j] = "◈";
                                storePlaced++;
                            }
                        }
                    }
                }
            }
        }

        //가벽과 상자 생성
        int comboCount = 0;
        int attempt = 0;

        while (comboCount < 2 && attempt < 1000) {
            attempt++;
            for (int m = 0; m < 3; m++) {
                for (int i = 2; i < ROW - 2; i++) {
                    for (int j = 2; j < COL - 2; j++) {
                        if (!maps[m][i][j].equals("■")) continue;

                        // 상자 위치(i,j)는 상하좌우가 모두 벽이어야 함
                        if (!(maps[m][i - 1][j].equals("■") && maps[m][i + 1][j].equals("■") &&
                                maps[m][i][j - 1].equals("■") && maps[m][i][j + 1].equals("■"))) continue;

                        // 상 방향 검사: 길-가벽-상자
                        if (maps[m][i - 2][j].equals("□") || maps[m][i - 2][j].equals("@")) {
                            if (maps[m][i - 1][j].equals("■")) {
                                maps[m][i - 1][j] = "▨";
                                maps[m][i][j] = "▣";
                                comboCount++;
                                break;
                            }
                        }

                        // 하 방향 검사: 상자-가벽-길
                        else if (maps[m][i + 2][j].equals("□") || maps[m][i + 2][j].equals("@")) {
                            if (maps[m][i + 1][j].equals("■")) {
                                maps[m][i + 1][j] = "▨";
                                maps[m][i][j] = "▣";
                                comboCount++;
                                break;
                            }
                        }

                        // 좌 방향 검사: 길-가벽-상자
                        else if (maps[m][i][j - 2].equals("□") || maps[m][i][j - 2].equals("@")) {
                            if (maps[m][i][j - 1].equals("■")) {
                                maps[m][i][j - 1] = "▨";
                                maps[m][i][j] = "▣";
                                comboCount++;
                                break;
                            }
                        }

                        // 우 방향 검사: 상자-가벽-길
                        else if (maps[m][i][j + 2].equals("□") || maps[m][i][j + 2].equals("@")) {
                            if (maps[m][i][j + 1].equals("■")) {
                                maps[m][i][j + 1] = "▨";
                                maps[m][i][j] = "▣";
                                comboCount++;
                                break;
                            }
                        }
                    }
                    if (comboCount >= 2) break;
                }
                if (comboCount >= 2) break;
            }
        }


    }


    public static void saveMaps(String userID) {
        for (int m = 0; m < 3; m++) {
            int monsterCount = 0;
            for (int i = 0; i < ROW; i++) {
                for (int j = 0; j < COL; j++) {
                    if (maps[m][i][j].equals("♤")) {
                        monsterCount++;
                    }
                }
            }
            if (monsterCount < 2) {
                System.out.printf("%d번째 맵의 몬스터가 부족합니다. 재생성합니다.\n", m + 1);
                regenerateMonstersInMap(m);
            }
        }
        String mapPath = "maps/" + userID + ".txt";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(mapPath))) {
            for(int m = 0; m<3; m++) {
                for (int i = 0; i < ROW; i++) {
                    for(int j = 0; j<COL; j++) {
                        writer.write(maps[m][i][j] + " ");
                    }
                    writer.write("\n");
                }
                writer.write("\n");

            }
        } catch (IOException e) {
            System.out.println("맵 데이터를 저장하지 못했습니다.");
        }
    }

    public static void savePlayerState(String userId, Player player) {
        try {
            Player updatedPlayer = new Player(
                    player.getBaseMaxHp(),
                    player.getCurrentHp(),
                    player.getBaseStr(),
                    new PlayerPosition(currentMap, playerX, playerY),
                    player.getItems(),
                    player.getGold()
            );
            JsonService.saveById(userId, updatedPlayer, Player.class);
        } catch (Exception e) {
            System.out.println("플레이어 저장 중 오류 발생");
        }
    }

    public static void pause() {
        System.out.println("\n계속하려면 Enter를 누르세요...");
        scanner.nextLine();
    }


    public static void clearScreen() {
        for (int i = 0; i < 30; i++) {
            System.out.println();
        }
    }

    public static void regenerateMonsters() {
        for (int m = 0; m < 3; m++) {
            // 현재 맵 몬스터 개수 세기
            int monsterCount = 0;
            for (int i = 0; i < ROW; i++) {
                for (int j = 0; j < COL; j++) {
                    if (maps[m][i][j].equals("♤")) {
                        monsterCount++;
                    }
                }
            }

            // 만약 2마리 이하라면, 몬스터 재배치
            if (monsterCount <= 2) {
                System.out.printf("%d번째 맵의 몬스터를 재생성합니다.\n", m+1);

                // 기존 ♤ 몬스터 흔적은 이미 없음(또는 무시), 그냥 추가
                int newMonsterCount = 0;
                for (int i = 1; i < ROW - 1; i++) {
                    for (int j = 1; j < COL - 1; j++) {
                        if (maps[m][i][j].equals("□")) {
                            // 상하좌우 벽 확인해서 몬스터 배치
                            int[][] directions = {{-1,0},{1,0},{0,-1},{0,1}};
                            for (int[] d : directions) {
                                int ni = i + d[0];
                                int nj = j + d[1];
                                if (maps[m][ni][nj].equals("■") && rand.nextInt(5) == 0 && nj != 0 && nj != COL-1) { // 20% 확률
                                    maps[m][ni][nj] = "♤";
                                    newMonsterCount++;
                                    break; // 하나 배치했으면 방향 루프 종료
                                }
                            }
                        }
                        if (newMonsterCount >= 10) break;
                    }
                    if (newMonsterCount >= 10) break;
                }

                // 혹시라도 2마리 안 되면 강제 배치
                while (newMonsterCount < 2) {
                    for (int i = 1; i < ROW - 1; i++) {
                        for (int j = 1; j < COL - 1; j++) {
                            if (maps[m][i][j].equals("□")) {
                                int[][] directions = {{-1,0},{1,0},{0,-1},{0,1}};
                                for (int[] d : directions) {
                                    int ni = i + d[0];
                                    int nj = j + d[1];
                                    if (maps[m][ni][nj].equals("■") && nj != 0 && nj != COL-1) {
                                        maps[m][ni][nj] = "♤";
                                        newMonsterCount++;
                                        break;
                                    }
                                }
                            }
                            if (newMonsterCount >= 2) break;
                        }
                        if (newMonsterCount >= 2) break;
                    }
                }
            }
        }
    }

    //몹 재생성용
    public static void checkAndRegenerateAllMonsters() {
        for (int m = 0; m < 3; m++) {
            int monsterCount = 0;
            for (int i = 0; i < ROW; i++) {
                for (int j = 0; j < COL; j++) {
                    if (maps[m][i][j].equals("♤")) {
                        monsterCount++;
                    }
                }
            }
            if (monsterCount == 0) {
                System.out.printf("%d번째 맵의 모든 몬스터가 제거되었습니다! 몬스터를 재생성합니다.\n", m + 1);
                pause();
                regenerateMonstersInMap(m);
            }
        }
    }


    //상자 열기
    public static void openChest(Player player, List<Item> items) {
        int goldDrop = rand.nextInt(21) + 30; // 30부터 50 사이
        player.setGold(player.getGold() + goldDrop);
        clearScreen();
        System.out.println("===================================");
        System.out.println("상자를 열었습니다!");
        System.out.println("===================================");
        System.out.printf("| %d 골드를 획득했습니다!           |%n", goldDrop);
        System.out.println("|                                |");
        System.out.println("|                                |");
        System.out.println("|________________________________|");
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

        int p = rand.nextInt(100);
        String lootName;
        if (p < 30) {               // 30%
            lootName = "longsword";
            clearScreen();
            System.out.println("|전리품으로 장검을 획득했습니다!      |");
            System.out.println("|                                |");
            System.out.println("|                                |");
            System.out.println("|________________________________|");
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        }
        else if (p < 60) {          // 다음 30%
            lootName = "armor";
            clearScreen();
            System.out.println("|전리품으로 갑옷을 획득했습니다!      |");
            System.out.println("|                                |");
            System.out.println("|                                |");
            System.out.println("|________________________________|");
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        }
        else {                      // 나머지 40%
            lootName = "largeHpPotion";
            clearScreen();
            System.out.println("|전리품으로 대용량 체력 포션을 획득했습니다!|");
            System.out.println("|                                |");
            System.out.println("|                                |");
            System.out.println("|________________________________|");
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        }
        ItemName newItem = ItemName.valueOf(lootName);
        boolean added = tryAddItemToInventory(player, newItem, 1);
        if (added) {
            System.out.printf("%s(1개) 인벤토리에 추가되었습니다.%n",
                    ItemDetail.getDetail(newItem).getKor());
        } else {
            System.out.printf("%s을(를) 획득하지 못했습니다.%n",
                    ItemDetail.getDetail(newItem).getKor());
        }
        System.out.println();

        maps[currentMap][playerX][playerY] = "□";
    }

    public static boolean tryAddItemToInventory(Player player, ItemName itemName, int count) {
        List<Item> inventory = new ArrayList<>();
        for (Item item : player.getItems()) {
            if (item.getCount() > 0) {
                inventory.add(item);
            }
        }
        // (1) 현재 무게 계산
        int currentWeight = player.getCurrentCarryWeight();
        // (2) 새로 추가될 아이템의 무게 (아이템 하나당 weight × 개수)
        int unitWeight = ItemDetail.getDetail(itemName).getWeight();
        int addedWeight = unitWeight * count;
        int newWeight = currentWeight + addedWeight;

        // (3) 허용 초과 검사
        if (newWeight > Player.MAX_CARRY_WEIGHT) {
            System.out.printf("현재 무게: %d / 최대 무게: %d. 새로 든 무게: %d 만큼 초과되었습니다.%n", currentWeight, Player.MAX_CARRY_WEIGHT, newWeight - Player.MAX_CARRY_WEIGHT);
            String choice;
            while (true) {
                System.out.println("1. 기존 아이템 버리고 수용   2. 획득 포기");
                System.out.print("선택: ");
                choice = scanner.nextLine().trim();

                if ("1".equals(choice) || "2".equals(choice)) break;
                System.out.println("잘못된 입력입니다. 1 또는 2를 입력해주세요.");
            }

            if ("2".equals(choice)) {
                // 2번: 획득을 포기
                System.out.println("아이템 획득을 포기했습니다.");
                return false;
            }
            // 1번: 기존 아이템 버리기 → 충분한 무게가 날 때까지 반복해서 버리도록
            while (true) {
                // (3-1) 충분히 무게를 비우기 위한 안내
                System.out.printf("아이템 한 종류를 골라 버려서 무게를 확보하십시오. (현재 무게: %d / 최대 %d)%n", currentWeight, Player.MAX_CARRY_WEIGHT);
                // 인벤토리에 실제로 존재하는 아이템 목록 출력
                for (int i = 0; i < inventory.size(); i++) {
                    Item it = inventory.get(i);
                    String kor = ItemDetail.getDetail(it.getName()).getKor();
                    System.out.printf("%d. %s(%d개, 무게:%d) %n", i + 1, kor, it.getCount(), ItemDetail.getDetail(it.getName()).getWeight());
                }
                System.out.println("0. 취소 및 획득 포기");
                System.out.print("버릴 아이템 번호: ");
                String line = scanner.nextLine().trim();
                int sel;
                try {
                    sel = Integer.parseInt(line);
                } catch (NumberFormatException e) {
                    System.out.println("숫자를 입력하십시오.");
                    continue;
                }
                if (sel == 0) {
                    System.out.println("획득을 포기합니다.");
                    return false;
                }
                if (sel < 1 || sel > inventory.size()) {
                    System.out.println("목록에 없는 번호입니다.");
                    continue;
                }
                // 해당 아이템 개수 1개 버리기
                Item toDrop = inventory.get(sel - 1);
                toDrop.setCount(toDrop.getCount() - 1);
                System.out.printf("%s 1개를 버렸습니다.%n",
                        ItemDetail.getDetail(toDrop.getName()).getKor());
                if (toDrop.getCount() == 0) {
                    inventory.remove(sel - 1);
                }
                // 버린 만큼 무게 갱신
                currentWeight = player.getCurrentCarryWeight();
                if (currentWeight + addedWeight <= Player.MAX_CARRY_WEIGHT) {
                    // 이제 충분히 여유가 생겼으므로 추가
                    break;
                } else {
                    System.out.printf("아직 무게가 부족합니다. (현재: %d / %d)%n", currentWeight, Player.MAX_CARRY_WEIGHT);
                }
            }
        }

        // (4) 실제로 인벤토리에 추가
        for (Item it : inventory) {
            if (it.getName() == itemName) {
                it.setCount(it.getCount() + count);
                return true;
            }
        }
        // 기존에 없는 아이템이면 항목 자체를 새로 추가
        inventory.add(new Item(itemName, count));
        return true;
    }

    public static void regenerateMonstersInMap(int m) {
        int monsterCount = 0;

        for (int i = 1; i < ROW - 1; i++) {
            for (int j = 1; j < COL - 1; j++) {
                if (maps[m][i][j].equals("□")) {
                    int[][] directions = {{-1,0},{1,0},{0,-1},{0,1}};
                    for (int[] d : directions) {
                        int ni = i + d[0];
                        int nj = j + d[1];
                        if (maps[m][ni][nj].equals("■") && rand.nextInt(10) == 0 && nj != 0 && nj != COL-1) {
                            maps[m][ni][nj] = "♤";
                            monsterCount++;
                            break;
                        }
                    }
                }
                if (monsterCount >= 10) break;
            }
            if (monsterCount >= 10) break;
        }

        while (monsterCount < 2) {
            for (int i = 1; i < ROW - 1; i++) {
                for (int j = 1; j < COL - 1; j++) {
                    if (maps[m][i][j].equals("□")) {
                        int[][] directions = {{-1,0},{1,0},{0,-1},{0,1}};
                        for (int[] d : directions) {
                            int ni = i + d[0];
                            int nj = j + d[1];
                            if (maps[m][ni][nj].equals("■") && nj != 0 && nj != COL-1) {
                                maps[m][ni][nj] = "♤";
                                monsterCount++;
                                break;
                            }
                        }
                    }
                    if (monsterCount >= 2) break;
                }
                if (monsterCount >= 2) break;
            }
        }
    }

}

