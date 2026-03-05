package services;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import models.User;
import org.mindrot.jbcrypt.BCrypt;
import models.Player;
import models.PlayerPosition;
import models.Item;
import models.ItemName;

/**
 * 사용자 인증 및 계정 관리를 담당하는 서비스 클래스
 * 로그인, 회원가입, 사용자 정보 관리 기능을 제공합니다.
 */
public class AuthService {
    // 상수 정의
    private static final Scanner SCANNER = new Scanner(System.in);
    private static final String USERS_FILE = "data/users.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // 비밀번호 해싱 강도 (BCrypt)
    private static final int BCRYPT_COST = 12;

    // 사용자 데이터
    private Map<String, User> userMap;
    private User currentUser;

    /**
     * AuthService 생성자
     * 사용자 정보를 JSON 파일에서 로드합니다.
     */
    public AuthService() {
        userMap = new HashMap<>();
        loadUsersJson();
    }

    /**
     * 현재 로그인된 사용자의 ID를 반환합니다.
     *
     * @return 로그인된 사용자 ID, 로그인되지 않은 경우 null
     */
    public String getUserId() {
        return currentUser != null ? currentUser.getId() : null;
    }

    /**
     * 현재 로그인된 사용자의 닉네임을 반환합니다.
     *
     * @return 로그인된 사용자 닉네임, 로그인되지 않은 경우 null
     */
    public String getUserNickname() {
        return currentUser != null ? currentUser.getNickname() : null;
    }

    /**
     * 인증 서비스를 시작하고 사용자 로그인/회원가입 메뉴를 표시합니다.
     * 로그인 또는 회원가입이 성공할 때까지 반복합니다.
     */
    public void start() throws IOException {
        boolean isAuthenticated = false;

        while (!isAuthenticated) {
            displayAuthMenu(); // 사용자 메뉴 출력

            String input = SCANNER.nextLine().trim(); // 무조건 문자열로 받고

            // 빈 문자열이면 다시
            if (input.isEmpty()) {
                System.out.println("잘못된 입력입니다. 숫자를 입력해주세요.");
                continue;
            }

            int choice;
            try {
                choice = Integer.parseInt(input); // 문자열을 정수로 변환
            } catch (NumberFormatException e) {
                System.out.println("잘못된 입력입니다. 숫자를 입력해주세요.");
                continue;
            }

            // 잘못된 숫자 입력 방지
            if (choice < 1 || choice > 3) {
                System.out.println("잘못된 입력입니다. 1, 2, 3 중에서 선택해주세요.");
                continue;
            }

            // 여기까지 오면 정상 선택한 것

            // 메뉴 선택에 따른 동작
            if (choice == 1) {
                System.out.println("[로그인 화면으로 이동합니다]");
                if (login(false)) {
                    System.out.println("로그인 성공");
                    isAuthenticated = true;
                } else {
                    System.out.println("로그인 실패, 재시도합니다.");
                }
            } else if (choice == 2) {
                System.out.println("[회원가입 화면으로 이동합니다]");
                if (register()) {
                    System.out.println("회원가입 성공");
                    isAuthenticated = true;  // ✅ 회원가입 성공 후 바로 인증 완료 처리
                } else {
                    System.out.println("회원가입 실패, 재시도합니다.");
                }
            } else if (choice == 3) {
                System.out.println("[프로그램을 종료합니다]");
                exit();
            }
        }
    }




    /**
     * 인증 메뉴를 화면에 표시합니다.
     * 테두리와 정렬을 사용하여 시각적으로 향상된 메뉴를 제공합니다.
     */
    private void displayAuthMenu() {
        String border = "+=========================+";
        String emptyLine = "|                         |";

        System.out.println(border);
        System.out.println("|     로그인 시스템       |");
        System.out.println(border);
        System.out.println(emptyLine);
        System.out.println("|    1. 로그인           |");
        System.out.println("|    2. 회원가입          |");
        System.out.println("|    3. 종료             |");
        System.out.println(emptyLine);
        System.out.println(border);
        System.out.print("선택 > ");
    }

    /**
     * 사용자 로그인 처리
     *
     * @param isAutoLogin 자동 로그인 여부 (회원가입 후 자동 로그인 시 true)
     * @return 로그인 성공 여부
     */
    public boolean login(boolean isAutoLogin) {
        while (true) {
            System.out.print("아이디를 입력하세요: ");
            String id = SCANNER.nextLine().trim().replaceAll("\\s+", "");
            if (id.isEmpty()) {

                continue;
            }

            if (!userMap.containsKey(id)) {
                System.out.println("존재하지 않는 아이디입니다.");
                continue;
            }

            System.out.print("비밀번호를 입력하세요: ");
            String password = SCANNER.nextLine().trim().replaceAll("\\s+", "");

            if (!isValidPassword(password)) {
                System.out.println("비밀번호 형식이 올바르지 않습니다.");
                continue;
            }

            User user = userMap.get(id);
            if (BCrypt.checkpw(password, user.getPassword())) {
                currentUser = user;
                user.setLastLoginedAt(System.currentTimeMillis());
                saveUsersJson();
                return true; //  로그인 성공 시 종료
            } else {
                System.out.println("비밀번호가 일치하지 않습니다.");
            }
        }
    }


    /**
     * 회원가입 처리
     *
     * @return 회원가입 성공 여부
     */
    public boolean register() throws IOException {
        String id;
        while (true) {
            System.out.print("새로운 아이디를 입력하세요 (4-12자의 소문자):");
            id = SCANNER.nextLine().trim();

            //  중간 공백 제거
            id = id.replaceAll("\\s+", "");

            if (!isValidId(id)) {
                System.out.println("아이디는 4-12자의 소문자만 가능합니다.");
                continue;
            }
            if (userMap.containsKey(id)) {
                System.out.println("이미 사용 중인 아이디입니다.");
                continue;
            }
            break;
        }


        String password;
        while (true) {
            System.out.print("비밀번호를 입력하세요 (8-20자, 대/소문자, 숫자, 특수문자 포함):");
            password = SCANNER.nextLine().trim().replaceAll("\\s+", "");
            if (!isValidPassword(password)) {
                System.out.println("형식에 맞지 않습니다.");
                continue;
            }
            break;
        }

        // 닉네임 입력 및 유효성/중복성 검사
        String nickname;
        while (true) {
            System.out.print("닉네임을 입력하세요 (4~12자, 영문/숫자/_.- 사용 가능):");
            nickname = SCANNER.nextLine().trim().replaceAll("\\s+", "");

            // 유효성 검사 함수로 검사
            if (!isValidNickname(nickname)) {
                System.out.println("닉네임 형식이 잘못되었습니다.");
                continue;
            }

            // 중복 검사
            boolean isDuplicate = false;
            for (User user : userMap.values()) {
                if (user.getNickname().equals(nickname)) {
                    System.out.println("이미 사용 중인 닉네임입니다.");
                    isDuplicate = true;
                    break;
                }
            }

            if (!isDuplicate) break;
        }

        // 사용자 생성 및 저장
        String hashedPassword = getHashPassword(password);
        User newUser = new User(id, nickname, hashedPassword, 0);
        userMap.put(id, newUser);
        saveUsersJson();

        // 신규 플레이어 생성
        if (!createNewPlayer(id)) {
            return false;
        }

        // 회원가입 시 자동 로그인을 위해 현재 사용자로 설정
        currentUser = newUser;
        return true;
    }

    /**
     * 신규 플레이어 데이터 생성
     *
     * @param userId 사용자 ID
     * @return 플레이어 생성 성공 여부
     */
    private boolean createNewPlayer(String userId) throws IOException {
        // 초기 위치 설정
        PlayerPosition position = new PlayerPosition(0, 0, 0); // mapId, x, y

        // 초기 아이템 설정
        Item[] items = {
                new Item(ItemName.hpPotion, 10),
                new Item(ItemName.largeHpPotion, 10),
                new Item(ItemName.bomb, 10),
                new Item(ItemName.dagger, 0),
                new Item(ItemName.longsword, 0),
                new Item(ItemName.shield, 0),
                new Item(ItemName.armor, 0),
                new Item(ItemName.bossKey, 0)
        };

        // 플레이어 객체 생성 (maxHp, currentHp, str, position, items, gold)
        Player newPlayer = new Player(100, 100, 30, position, items, 0);

        // items[] 클래스 분리
        items = JsonService.getTypedItems(newPlayer);
        newPlayer.setItems(items);

        try {
            JsonService.saveById(userId, newPlayer, Player.class);
            return true;
        } catch (IOException e) {
            System.out.println("플레이어 데이터 저장 중 오류 발생");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 회원인증 서비스 종료
     * 리소스를 정리하고 프로그램을 종료합니다.
     */
    public void exit() {
        System.out.println("게임을 종료합니다.");
        SCANNER.close();
        System.exit(0);
    }

    /**
     * 아이디 유효성 검사
     *
     * @param id 검사할 아이디
     * @return 유효한 아이디인지 여부
     */
    private boolean isValidId(String id) {
        return id != null &&
						id.length() >= 4 &&
						id.length() <= 12 &&
						id.matches("^[a-z]+$") &&
						!id.contains(" ");
    }

    /**
     * 비밀번호 유효성 검사
     * 비밀번호 유효성 검사
     *
     * @param password 검사할 비밀번호
     * @return 유효한 비밀번호인지 여부
     */
    private boolean isValidPassword(String password) {
        // 8-20자이며, 대소문자, 숫자, 특수문자를 포함하는지 검사
        return password != null &&
						password.length() >= 8 &&
						password.length() <= 20 &&
						!password.contains(" ") &&
						password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+=\\-{}\\[\\]:;\"'<>,.?/]).*$");
    }


    /**
     * 닉네임 유효성 검사
     *
     * @param nickname 검사할 닉네임
     * @return 유효한 닉네임인지 여부
     */
    private boolean isValidNickname(String nickname) {
        return nickname != null &&
						nickname.matches("^[a-zA-Z0-9가-힣_.\\-]{4,12}$");
    }
    /**
     * 비밀번호 해시 처리
     *
     * @param password 원본 비밀번호
     * @return BCrypt로 해시된 비밀번호
     */
    private String getHashPassword(String password) {
        try {
            return BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_COST));
        } catch (Exception e) {
            throw new RuntimeException("비밀번호 해시 처리 실패", e);
        }
    }

    /**
     * JSON 파일에서 사용자 정보 로드
     * 파일이 없거나 로드 실패 시 빈 맵 생성
     */
    private void loadUsersJson() {
        Path filePath = Paths.get(USERS_FILE);

        try {
            if (Files.exists(filePath)) {
                try (Reader reader = Files.newBufferedReader(filePath)) {
                    Type type = new TypeToken<HashMap<String, User>>(){}.getType();
                    Map<String, User> loadedMap = GSON.fromJson(reader, type);

                    // null 체크 후 할당
                    if (loadedMap != null) {
                        userMap = loadedMap;
                    }
                }
                System.out.println("사용자 데이터 로드 성공");
            } else {
                // 디렉토리가 없으면 생성
                Files.createDirectories(filePath.getParent());
                System.out.println("사용자 데이터 파일이 없어 새로 생성합니다.");
            }
        } catch (IOException e) {
            System.out.println("사용자 데이터 로드 실패: " + e.getMessage());
        }
    }

    /**
     * 사용자 정보를 JSON 파일로 저장
     */
    private void saveUsersJson() {
        Path filePath = Paths.get(USERS_FILE);

        try {
            // 디렉토리가 없으면 생성
            Files.createDirectories(filePath.getParent());

            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(userMap, writer);
            }
        } catch (IOException e) {
            System.out.println("사용자 데이터 저장 실패: " + e.getMessage());
        }
    }
}
