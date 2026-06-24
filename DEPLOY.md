# 배포 / CI·CD 가이드

## 구성 개요

| 단계 | 트리거 | 동작 |
|------|--------|------|
| **CI** (`.github/workflows/ci.yml`) | PR → main | Gradle 빌드 + 테스트 |
| **CD** (`.github/workflows/cd.yml`) | push → main | 빌드·테스트 → Docker 이미지 빌드 → **GHCR 푸시** → (옵션) **SSH 배포** |

이미지: `ghcr.io/junse02/wontodollaranalysis:latest` (+ 커밋 SHA 태그)

## 로컬에서 Docker로 실행

```bash
# 환경변수 준비
export NAVER_CLIENT_ID=xxxx
export NAVER_CLIENT_SECRET=xxxx
export GEMINI_API_KEY=xxxx   # 선택: 뉴스 LLM 분류 (없으면 키워드 매칭)

# 소스로 빌드해 실행
docker compose up -d --build

# 확인
curl http://localhost:8080/actuator/health   # {"status":"UP"}
```

데이터(H2 파일)는 `app-data` 볼륨에 영속됩니다.

## 자동 배포(CD) 활성화 방법

이미지 빌드·GHCR 푸시는 **추가 설정 없이** 동작합니다(`GITHUB_TOKEN` 사용).
실제 서버 반영(`deploy` 잡)은 아래를 설정해야 실행됩니다.

### 1. 배포 서버 준비 (Docker 설치된 Linux 호스트)
- Docker + docker compose 설치
- 배포용 SSH 키 등록 (공개키를 서버 `~/.ssh/authorized_keys`에)

### 2. GitHub 저장소 변수(Variables)
| 이름 | 값 | 용도 |
|------|-----|------|
| `DEPLOY_ENABLED` | `true` | 이 값이 `true`일 때만 deploy 잡 실행 |

`Settings → Secrets and variables → Actions → Variables` 에서 추가

### 3. GitHub 저장소 시크릿(Secrets)
| 이름 | 설명 |
|------|------|
| `DEPLOY_HOST` | 서버 IP/도메인 |
| `DEPLOY_USER` | SSH 사용자 |
| `DEPLOY_SSH_KEY` | SSH 개인키 (PEM 전체) |
| `DEPLOY_PORT` | SSH 포트 (예: 22) |
| `NAVER_CLIENT_ID` | 네이버 검색 API 클라이언트 ID |
| `NAVER_CLIENT_SECRET` | 네이버 검색 API 시크릿 |
| `GEMINI_API_KEY` | (선택) 뉴스 LLM 분류용 Gemini 키. 미설정 시 키워드 매칭으로 폴백 |
| `GHCR_PULL_TOKEN` | (이미지가 private일 때만) `read:packages` 권한 PAT |

> GHCR 패키지를 **public**으로 공개하면 `GHCR_PULL_TOKEN` 없이도 서버에서 pull 가능합니다.
> (GitHub → Packages → 해당 패키지 → Package settings → Change visibility)

### 동작 방식
`main` 푸시 시:
1. 빌드·테스트 → 이미지 빌드 → `ghcr.io/...:latest`, `:<sha>` 푸시
2. `DEPLOY_ENABLED=true`면: `docker-compose.yml`을 서버 `~/wontodollar`로 복사 →
   시크릿으로 `.env` 생성 → `docker compose pull && up -d` → 이전 이미지 정리

## 프로필
- 로컬: 기본 `local` 프로필 (`application-local.properties`에 실제 키, H2 콘솔 활성)
- 운영: `prod` 프로필 (Docker가 `SPRING_PROFILES_ACTIVE=prod` 설정, H2 콘솔 비활성, 헬스만 노출)
