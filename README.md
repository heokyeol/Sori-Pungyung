# 사운드드라이브 (SoundDrive)

차 안·차박용 입체 배경 소리 앱. 차를 가운데 두고 소리를 원하는 방향에 끌어다 놓는다. 현대 Pleos(Android Automotive) 공모전용.

- **Pleos(차량)판**: `app/src/pleos` — 주 개발 대상
- **웹판**: `web/` — https://sounds.homesev.duckdns.org
- **갤럭시(휴대폰)판**: `app/src/galaxy` — 더 이상 업데이트하지 않음(코드만 유지)
- 소리 엔진·화면 공통 코드: `app/src/main`

## 기능
- 소리 10개(빗소리·파도·바람·모닥불·새 12종 숲·앰비언트·천둥번개·우주배경복사·토성·목성), 모두 실제 녹음(CC0 위주, 출처는 앱의 "소리 출처" / `app/src/main/assets/CREDITS.txt`)
- 입체 배치, 행성 궤도·360° 회전, 수면 타이머(마지막 10분 페이드), 백그라운드 재생, 화면 어둡게
- **지역 소리(기본 해금)**: 정해 둔 지역 26곳에 가면 열림 — 예) 영종도 → 비행기 기내, 의왕 철도박물관 → 기차 안 (`assets/places.tsv`)
- **계절 명소(이벤트)**: 지역+계절 12곳 — 예) 여름 양양 → 서핑, 겨울 대관령 → 눈보라, 가을 마이산 → 낙엽 (`Seasons.java`)
- 소리 도감, 주변 해금 장소 레이더·지도(MapLibre + OpenFreeMap, 키 없음)
- 앱 안에 지도/장소 API 없음: 좌표는 OpenStreetMap에서 한 번만 뽑아 넣음

### Pleos판 전용 (공개 Pleos SDK)
| 기능 | SDK |
|---|---|
| 속도 볼륨 보정·주행 중 조작 잠금, 창문 열리면 소리 줄임, 무드등 공간 그라데이션 | Vehicle 2.3.2 |
| 위치 해금 | FusedLocation 1.1.3 |
| 음성 명령·음성 안내("빗소리 켜 줘", "이번 계절 명소 어디야") | SpeechToText / TextToSpeech 3.3.1 (온디바이스) |
| 순정 내비로 "여기로 안내" | NaviHelper 3.0.5 |

## 빌드·시험
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew assemblePleosDebug

# PC 시험 (소리·새·무드등·음성 명령·지역·계절)
J=/opt/homebrew/opt/openjdk@17/bin; P=app/src/main/java/dev/homesev/sounddrive
mkdir -p build/smoke && $J/javac -d build/smoke $P/{Noise,SoundSource,Sources,Spatial,Mixer,Birds,Unlocks,Seasons,VoiceCommands}.java app/src/pleos/java/dev/homesev/sounddrive/MoodGradient.java tests/dev/homesev/sounddrive/*.java
for t in AudioSmokeTest BirdsTest MoodGradientTest VoiceCommandsTest SeasonsTest; do $J/java -cp build/smoke dev.homesev.sounddrive.$t; done
$J/java -cp build/smoke dev.homesev.sounddrive.UnlocksTest app/src/main/assets/places.tsv
```

### 소리·지역 자료 다시 만들기 (`tools/`, Python venv + soundfile)
- `commons_search.py`, `freesound_search.py` 녹음 찾기 → `sound_manifest.py` 고른 목록 → `make_assets.py` 반복 구간·새 울음 조각·출처 생성
- `osm_places.py`, `geocode_spots.py`, `geocode_regions.py` 좌표 1회 조회
- `make_web_assets.py` 웹용 mp3·places.json 생성 (web/audio는 저장소에 넣지 않음)

## 남은 일
- 기기 확인: 무드등·창문·음성 SDK·내비 안내·실제 위치 해금
- 디자인 개편(방향 결정 대기), 부르면 듣기(운전대 버튼 방식 추천, 결정 대기)
- 대구 라이온즈파크·창원NC파크 좌표 미확인으로 빠짐
