# Poke Quest (가이드 퀘스트)

첫 포획부터 각 지방의 체육관, 리그, 챔피언까지 무엇을 해야 하는지 알려 주는 가이드 퀘스트입니다. 단계마다 보상이 있고, 중간중간 라이벌이 기다립니다.

- 버전: 1.2.2
- Minecraft 1.21.1 / Fabric
- 필요한 모드: Cobblemon, Radical Cobblemon Trainers (rctmod, rctapi), Fabric API
- 설치: 서버에만 넣으면 됩니다. 접속하는 사람(클라이언트)은 이 모드가 없어도 됩니다.

## 다운로드

**[⬇ poke-quest-1.2.2.jar 다운로드](https://github.com/minerapai/poke-quest/releases/download/v1.2.2/poke-quest-1.2.2.jar)**

받은 jar 파일을 마인크래프트(또는 서버) 폴더의 `mods` 폴더에 넣으면 됩니다. 다른 버전은 [Releases](https://github.com/minerapai/poke-quest/releases) 에 있습니다.

## 필요한 데이터팩 ⚠

| 데이터팩 | 없으면 |
|---|---|
| **COBBLEVERSE-RCT-DP** (COBBLEVERSE 모드팩에 들어 있음) | 관동~신오 관장, 사천왕, 챔피언이 없어서 퀘스트를 진행할 수 없습니다. |
| **[Unova-Paldea Leaders](https://github.com/minerapai/unova-paldea-leaders)** (따로 설치) | 하나~팔데아 시리즈가 없어서 `/시리즈` 화면에서 그 지방들이 잠긴 채로 나오고, 신오 다음으로 진행할 수 없습니다. |

Unova-Paldea Leaders의 관장 65명은 월드에 저절로 나타나지 않고, 이 모드의 **[다음 상대에게 도전]**으로 불러내야 만날 수 있습니다. 그래서 그 데이터팩을 쓰려면 이 모드가 꼭 있어야 합니다.

## 기능

- 퀘스트 화면에서 지금 할 일과 보상 확인
- [다음 상대에게 도전]: 다음 관장/사천왕/챔피언을 눈앞에 불러내 바로 배틀 (찾아다닐 필요 없음, 배틀이 끝나면 사라짐)
- [시리즈(지방) 바꾸기]: 관동 → 성도 → 호연 → 신오 → 하나 → 칼로스 → 알로라 → 가라르 → 팔데아
- 라이벌 배틀 (내 레벨 상한에 맞춰 레벨 보정)

## 사용법

| 명령어 | 설명 |
|---|---|
| `/quest (/퀘스트)` | 퀘스트 화면 열기 |
| `/quest rival (/라이벌)` | 기다리는 라이벌과 배틀 |
| `/series (/시리즈)` | 시리즈(지방) 고르기 화면. 진행 중인 지방을 떠나면 진행이 사라지므로 쉬프트 클릭해야 바뀝니다. |
| `/시리즈 force <지방> [플레이어]` | (OP) 조건과 상관없이 지방 바꾸기 |
| `/quest reset [플레이어]` | (OP) 퀘스트 초기화 |
| `/quest reload` | (OP) 설정 다시 읽기 |

(OP) 표시가 있는 명령어는 서버 관리자(OP)만 쓸 수 있습니다.

## 빌드

```bash
./gradlew build
```

`build/libs/` 에 만들어진 jar 파일을 서버의 `mods` 폴더에 넣습니다.

## 라이선스

MIT License. 출처(minerapai)만 밝히면 누구나 자유롭게 쓰고 고치고 배포할 수 있습니다. 자세한 내용은 [LICENSE](LICENSE) 파일을 보세요.
