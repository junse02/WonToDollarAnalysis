# Eco_Analysis — 원/달러 환율 분석 대시보드

USD/KRW 환율을 수집·시각화하고, 환율 관련 뉴스 키워드를 분석해
**환율 변동의 원인을 추정**하는 Spring Boot 웹 애플리케이션입니다.

## 주요 기능

- **실시간 환율 조회** — Frankfurter API로 USD/KRW 환율을 가져옵니다 (API 키 불필요).
- **환율 히스토리 차트** — 최근 30일 환율을 일자별로 집계해 그래프로 표시합니다.
- **뉴스 키워드 분석** — 네이버 뉴스 검색 API에서 환율 관련 기사를 수집해 키워드 빈도를 분석합니다.
- **변동 원인 분석** — 날짜별 환율 변동과 그 시점의 뉴스 키워드를 매핑해 변동 원인을 추정합니다.
- **압력 지수 / 적중률** — 뉴스 기반 환율 압력 지수를 산출하고, 일별 스냅샷으로 예측 적중률을 누적 평가합니다.
- **자동 갱신** — 앱 시작 시 과거 데이터를 백필하고, 매 1시간마다 환율·스냅샷을 갱신합니다.
- **장애 대응** — 외부 API 응답 지연 시 DB의 마지막 저장값으로 폴백하고 안내 문구를 노출합니다.

## 기술 스택

| 영역 | 사용 기술 |
|------|-----------|
| 언어 / 런타임 | Java 21 |
| 프레임워크 | Spring Boot 3.3.5 (Web, JPA, Thymeleaf, Validation, Cache, Actuator) |
| 데이터베이스 | H2 (파일 모드, `./data/ecoanalysis.mv.db`) |
| 캐시 | Caffeine (5분 TTL) |
| 빌드 | Gradle (Wrapper 포함) |
| 외부 API | [Frankfurter](https://api.frankfurter.app) (환율), 네이버 뉴스 검색 API |
| 배포 | Docker / Docker Compose, GitHub Actions (CI·CD), GHCR |

## 시작하기

### 사전 준비

- JDK 21
- 네이버 검색 API 키 ([네이버 개발자 센터](https://developers.naver.com)에서 발급)

### 1. 네이버 API 키 설정

`src/main/resources/application-local.properties` 에 실제 키를 입력하거나,
환경변수로 주입합니다.

```properties
api.naver.client-id=YOUR_NAVER_CLIENT_ID
api.naver.client-secret=YOUR_NAVER_CLIENT_SECRET
```

```bash
# 또는 환경변수로
export NAVER_CLIENT_ID=xxxx
export NAVER_CLIENT_SECRET=xxxx
```

### 2. 로컬 실행

```bash
./gradlew bootRun
```

브라우저에서 http://localhost:8080 으로 접속합니다.

- H2 콘솔: http://localhost:8080/h2-console (local 프로필에서만 활성)
  - JDBC URL: `jdbc:h2:file:./data/ecoanalysis`, 사용자: `sa`, 비밀번호 없음
- 헬스 체크: http://localhost:8080/actuator/health

### 3. 테스트

```bash
./gradlew test
```

## API 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/` | 대시보드 (Thymeleaf 렌더링) |
| `GET` | `/api/rate/current` | 현재 USD/KRW 환율 |
| `GET` | `/api/rate/history?days=30` | 일자별 환율 히스토리 (1~365일) |
| `GET` | `/api/analysis` | 키워드 분석 + 변동 원인 + 압력 지수/적중률 |

## Docker로 실행

```bash
export NAVER_CLIENT_ID=xxxx
export NAVER_CLIENT_SECRET=xxxx

docker compose up -d --build
curl http://localhost:8080/actuator/health   # {"status":"UP"}
```

데이터(H2 파일)는 `app-data` 볼륨에 영속됩니다.

## 배포 (CI·CD)

- **CI** (`.github/workflows/ci.yml`): PR → main 시 Gradle 빌드·테스트
- **CD** (`.github/workflows/cd.yml`): push → main 시 빌드·테스트 → Docker 이미지 빌드 → GHCR 푸시 → (옵션) SSH 배포

자세한 배포 설정(시크릿·변수, 서버 준비, 자동 배포 활성화)은 [DEPLOY.md](DEPLOY.md)를 참고하세요.

## 프로필

| 프로필 | 용도 | 특징 |
|--------|------|------|
| `local` (기본) | 로컬 개발 | H2 콘솔 활성, 실제 키는 `application-local.properties` |
| `prod` | 운영 (Docker) | H2 콘솔 비활성, Actuator 헬스만 노출 |

## 프로젝트 구조

```
src/main/java/sung/eco_analysis/
├── config/        # API 설정 프로퍼티, RestClient 등 빈 구성
├── controller/    # 웹(Thymeleaf) / REST API / 전역 예외 처리
├── dto/           # 외부 API 응답 및 분석 결과 DTO
├── entity/        # RateHistory, DailySnapshot (JPA)
├── repository/    # Spring Data JPA 리포지토리
├── scheduler/     # 환율·스냅샷 자동 갱신 스케줄러
└── service/       # 환율 조회, 뉴스 수집, 키워드 분석, 스냅샷 평가
```