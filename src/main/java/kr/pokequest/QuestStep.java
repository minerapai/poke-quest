package kr.pokequest;

import java.util.ArrayList;
import java.util.List;

/**
 * One step of the guide quest, as written in config/poke-quest.json (steps are done in order).
 *
 * type / key / target:
 * - "stat": a custom statistic (key = stat id, e.g. "cobblemon:captured") reaches target
 * - "dex": species caught in the pokedex reaches target
 * - "defeat": the RCT trainer key (e.g. "kanto_brock") was beaten (or its whole series is completed)
 * - "series": the RCT series key ("kanto", ...) was completed
 * - "rival": beat the rival (battle number target, 1-based), started from /quest
 */
public record QuestStep(
    String id,
    String region,
    String name,
    String description,
    String type,
    String key,
    long target,
    long money,
    List<String> items,
    String icon
) {
    private static QuestStep step(String id, String region, String name, String description, String type, String key,
                                  long target, long money, String icon, String... items) {
        return new QuestStep(id, region, name, description, type, key, target, money, List.of(items), icon);
    }

    private static QuestStep gym(String region, String trainer, String who, String where, long money, String icon, String... items) {
        return step(trainer, region, who + " 격파", where + "의 " + who + "에게 이기기", "defeat", trainer, 1, money, icon, items);
    }

    private static QuestStep rival(int number, String region, String description, long money, String... items) {
        return step("rival_" + number, region, "라이벌과의 " + number + "번째 대결", description + " (/quest 에서 배틀)", "rival", "",
            number, money, "minecraft:target", items);
    }

    public static List<QuestStep> defaults() {
        List<QuestStep> s = new ArrayList<>();
        String k = "관동";
        s.add(step("first_catch", k, "첫 포획", "야생 포켓몬을 1마리 잡기", "stat", "cobblemon:captured", 1, 3_000,
            "cobblemon:poke_ball", "cobblemon:poke_ball 10", "cobblemon:potion 5"));
        s.add(step("catch_5", k, "동료 모으기", "포켓몬 5마리 잡기", "stat", "cobblemon:captured", 5, 3_000,
            "cobblemon:poke_ball", "cobblemon:poke_ball 10", "cobblemon:exp_candy_s 5"));
        s.add(rival(1, k, "라이벌이 실력을 겨뤄 보자고 한다", 5_000, "cobblemon:potion 5", "cobblemon:exp_candy_s 5"));
        s.add(gym(k, "kanto_brock", "웅", "회색시티 체육관", 8_000, "cobbleversebadges:kanto_boulder_badge",
            "cobblemon:great_ball 5", "cobblemon:super_potion 3"));
        s.add(step("first_evolve", k, "첫 진화", "포켓몬을 1번 진화시키기", "stat", "cobblemon:evolved", 1, 5_000,
            "cobblemon:rare_candy", "cobblemon:rare_candy 2", "cobblemon:exp_candy_m 5"));
        s.add(gym(k, "kanto_misty", "이슬", "블루시티 체육관", 10_000, "cobbleversebadges:kanto_cascade_badge",
            "cobblemon:great_ball 5", "cobblemon:revive 2"));
        s.add(step("dex_20", k, "도감 채우기", "도감에 포켓몬 20종 등록(포획)", "dex", "", 20, 8_000,
            "cobblemon:pokedex_red", "cobblemon:great_ball 10", "cobblemon:exp_candy_m 5"));
        s.add(gym(k, "kanto_ltsurge", "마티스", "갈색시티 체육관", 12_000, "cobbleversebadges:kanto_thunder_badge",
            "cobblemon:thunder_stone 1", "cobblemon:super_potion 5"));
        s.add(rival(2, k, "라이벌이 다시 나타났다", 12_000, "cobblemon:great_ball 10", "cobblemon:revive 3"));
        s.add(gym(k, "kanto_erika", "민화", "무지개시티 체육관", 14_000, "cobbleversebadges:kanto_rainbow_badge",
            "cobblemon:leaf_stone 1", "cobblemon:exp_candy_m 10"));
        s.add(step("first_hatch", k, "새 생명", "알을 1개 부화시키기 (목장에 두 마리를 맡기면 알이 생김)", "stat",
            "cobblemon:eggs_hatched", 1, 10_000, "minecraft:egg", "cobblemon:everstone 1", "cobblemon:destiny_knot 1"));
        s.add(gym(k, "kanto_koga", "독수", "연분홍시티 체육관", 16_000, "cobbleversebadges:kanto_soul_badge",
            "cobblemon:ultra_ball 5", "cobblemon:hyper_potion 3"));
        s.add(step("dex_50", k, "도감 연구", "도감에 포켓몬 50종 등록(포획)", "dex", "", 50, 15_000,
            "cobblemon:pokedex_blue", "cobblemon:ultra_ball 10", "cobblemon:exp_candy_l 5"));
        s.add(gym(k, "kanto_sabrina", "초련", "노랑시티 체육관", 18_000, "cobbleversebadges:kanto_marsh_badge",
            "cobblemon:moon_stone 1", "cobblemon:hyper_potion 5"));
        s.add(rival(3, k, "라이벌이 길을 막아섰다", 20_000, "cobblemon:ultra_ball 10", "cobblemon:rare_candy 3"));
        s.add(gym(k, "kanto_blaine", "강연", "홍련마을 체육관", 20_000, "cobbleversebadges:kanto_volcano_badge",
            "cobblemon:fire_stone 1", "cobblemon:max_revive 2"));
        s.add(gym(k, "kanto_giovanni", "비주기", "상록시티 체육관", 25_000, "cobbleversebadges:kanto_earth_badge",
            "cobblemon:pp_up 3", "cobblemon:exp_candy_l 10"));
        s.add(rival(4, k, "포켓몬리그 앞에서 라이벌이 기다리고 있다", 30_000, "cobblemon:full_restore 5", "cobblemon:rare_candy 5"));
        s.add(step("kanto_elite", k, "관동 사천왕", "칸나·시바·국화·목호를 모두 이기기", "defeat", "kanto_league_lance", 1, 40_000,
            "minecraft:diamond", "cobblemon:full_restore 5", "cobblemon:max_revive 3"));
        s.add(step("kanto_champion", k, "관동 챔피언", "챔피언 그린에게 이기기", "defeat", "kanto_champion_blue", 1, 60_000,
            "cobbleversebadges:kanto_league_trophy", "cobblemon:master_ball 1", "cobblemon:ability_capsule 1"));

        String j = "성도";
        s.add(rival(5, j, "성도 지방으로 떠나기 전에 라이벌이 찾아왔다", 30_000, "cobblemon:ultra_ball 10", "cobblemon:exp_candy_l 10"));
        s.add(gym(j, "johto_valerio", "비상", "도라지시티 체육관", 25_000, "cobbleversebadges:johto_zephyr_badge", "cobblemon:ultra_ball 10"));
        s.add(gym(j, "johto_raffaello", "호일", "고동마을 체육관", 25_000, "cobbleversebadges:johto_hive_badge", "cobblemon:hyper_potion 5"));
        s.add(gym(j, "johto_chiara", "꼭두", "금빛시티 체육관", 28_000, "cobbleversebadges:johto_plain_badge", "cobblemon:rare_candy 3"));
        s.add(gym(j, "johto_angelo", "유빈", "인주시티 체육관", 28_000, "cobbleversebadges:johto_fog_badge", "cobblemon:max_revive 2"));
        s.add(rival(6, j, "성도 지방에서 라이벌과 다시 마주쳤다", 35_000, "cobblemon:full_restore 5", "cobblemon:exp_candy_xl 5"));
        s.add(gym(j, "johto_furio", "사도", "진청시티 체육관", 30_000, "cobbleversebadges:johto_storm_badge", "cobblemon:pp_up 3"));
        s.add(gym(j, "johto_jasmine", "규리", "담청시티 체육관", 30_000, "cobbleversebadges:johto_mineral_badge", "cobblemon:ultra_ball 10"));
        s.add(gym(j, "johto_alfredo", "류옹", "황토마을 체육관", 32_000, "cobbleversebadges:johto_glacier_badge", "cobblemon:ice_stone 1"));
        s.add(gym(j, "johto_sandra", "이향", "검은먹시티 체육관", 35_000, "cobbleversebadges:johto_rising_badge", "cobblemon:rare_candy 5"));
        s.add(step("dex_100", j, "도감 박사의 길", "도감에 포켓몬 100종 등록(포획)", "dex", "", 100, 30_000,
            "cobblemon:pokedex_green", "cobblemon:ability_capsule 1", "cobblemon:exp_candy_xl 5"));
        s.add(step("johto_elite", j, "성도 사천왕", "일목·독수·시바·카렌을 모두 이기기", "defeat", "johto_league_karen", 1, 50_000,
            "minecraft:diamond", "cobblemon:full_restore 5", "cobblemon:max_revive 3"));
        s.add(step("johto_champion", j, "성도 챔피언", "챔피언 목호에게 이기기", "defeat", "johto_champion_lance", 1, 80_000,
            "cobbleversebadges:johto_league_trophy", "cobblemon:master_ball 1", "cobblemon:ability_patch 1"));

        String h = "호연";
        s.add(gym(h, "hoenn_petra", "원규", "금탄시티 체육관", 35_000, "cobbleversebadges:hoenn_stone_badge", "cobblemon:ultra_ball 10"));
        s.add(gym(h, "hoenn_rudi", "철구", "무로마을 체육관", 35_000, "cobbleversebadges:hoenn_knuckle_badge", "cobblemon:hyper_potion 5"));
        s.add(gym(h, "hoenn_walter", "암페어", "보라시티 체육관", 38_000, "cobbleversebadges:hoenn_dynamo_badge", "cobblemon:thunder_stone 1"));
        s.add(gym(h, "hoenn_fiammetta", "민지", "용암마을 체육관", 38_000, "cobbleversebadges:hoenn_heat_badge", "cobblemon:fire_stone 1"));
        s.add(rival(7, h, "호연 지방까지 라이벌이 따라왔다", 45_000, "cobblemon:full_restore 5", "cobblemon:exp_candy_xl 10"));
        s.add(gym(h, "hoenn_norman", "종길", "등화도시 체육관", 40_000, "cobbleversebadges:hoenn_balance_badge", "cobblemon:rare_candy 5"));
        s.add(gym(h, "hoenn_alice", "은송", "검방울시티 체육관", 40_000, "cobbleversebadges:hoenn_feather_badge", "cobblemon:max_revive 3"));
        s.add(gym(h, "hoenn_tell", "풍·란", "이끼시티 체육관", 42_000, "cobbleversebadges:hoenn_mind_badge", "cobblemon:pp_up 3"));
        s.add(gym(h, "hoenn_adriano", "아단", "루네시티 체육관", 45_000, "cobbleversebadges:hoenn_rain_badge", "cobblemon:water_stone 1"));
        s.add(step("dex_200", h, "도감 수집가", "도감에 포켓몬 200종 등록(포획)", "dex", "", 200, 40_000,
            "cobblemon:pokedex_yellow", "cobblemon:ability_capsule 1", "obc:bottle_cap 1"));
        s.add(step("hoenn_elite", h, "호연 사천왕", "혁진·회연·미혜·권수를 모두 이기기", "defeat", "hoenn_league_drake", 1, 60_000,
            "minecraft:diamond", "cobblemon:full_restore 5", "cobblemon:max_revive 3"));
        s.add(step("hoenn_champion", h, "호연 챔피언", "챔피언 성호에게 이기기", "defeat", "hoenn_champion_rocco", 1, 100_000,
            "cobbleversebadges:hoenn_league_trophy", "cobblemon:master_ball 1", "obc:bottle_cap_gold 1"));

        String n = "신오";
        s.add(gym(n, "sinnoh_pedro", "강석", "무쇠시티 체육관", 45_000, "cobbleversebadges:sinnoh_coal_badge", "cobblemon:ultra_ball 10"));
        s.add(gym(n, "sinnoh_gardenia", "유채", "영원시티 체육관", 45_000, "cobbleversebadges:sinnoh_forest_badge", "cobblemon:leaf_stone 1"));
        s.add(gym(n, "sinnoh_marzia", "자두", "들판시티 체육관", 48_000, "cobbleversebadges:sinnoh_cobble_badge", "cobblemon:hyper_potion 5"));
        s.add(gym(n, "sinnoh_omar", "맥실러", "연고시티 체육관", 48_000, "cobbleversebadges:sinnoh_fen_badge", "cobblemon:water_stone 1"));
        s.add(rival(8, n, "신오 지방에서 라이벌이 마지막 도전장을 내밀었다", 60_000, "cobblemon:full_restore 10", "cobblemon:exp_candy_xl 10"));
        s.add(gym(n, "sinnoh_fannie", "멜리사", "축복시티 체육관", 50_000, "cobbleversebadges:sinnoh_relic_badge", "cobblemon:dusk_stone 1"));
        s.add(gym(n, "sinnoh_ferruccio", "동관", "운하시티 체육관", 50_000, "cobbleversebadges:sinnoh_mine_badge", "cobblemon:pp_up 3"));
        s.add(gym(n, "sinnoh_bianca", "무청", "선단시티 체육관", 52_000, "cobbleversebadges:sinnoh_icicle_badge", "cobblemon:ice_stone 1"));
        s.add(gym(n, "sinnoh_corrado", "전진", "물가시티 체육관", 55_000, "cobbleversebadges:sinnoh_beacon_badge", "cobblemon:rare_candy 10"));
        s.add(step("sinnoh_elite", n, "신오 사천왕", "충호·들국화·대엽·오엽을 모두 이기기", "defeat", "sinnoh_league_luciano", 1, 80_000,
            "minecraft:diamond", "cobblemon:full_restore 10", "cobblemon:max_revive 5"));
        s.add(step("sinnoh_champion", n, "신오 챔피언", "챔피언 난천에게 이기기", "defeat", "sinnoh_champion_camilla", 1, 150_000,
            "cobbleversebadges:sinnoh_league_trophy", "cobblemon:master_ball 1", "obc:bottle_cap_gold 2"));
        s.add(rival(9, n, "모든 여행을 마친 당신에게 라이벌이 마지막 승부를 청한다", 200_000,
            "cobblemon:master_ball 1", "cobblemon:ability_patch 1", "obc:bottle_cap_gold 1"));
        return s;
    }
}
