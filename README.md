# DB 데이터 이관 엔진 (Oracle → PostgreSQL)

## Overview
- **Language**: Java 21  
- **Framework**: Spring Boot 3.2.4, Spring Batch 5.1.1  
- **Database**: Oracle (Source), PostgreSQL (Target)  
- **Library**: HikariCP, Spring JDBC (`NamedParameterJdbcTemplate`)  

---

## 주요 엔지니어링 결정 (Technical Decisions)

### 1. DTO-less Architecture (140+ 테이블 관리 최적화)

**Problem**  
- 테이블별 DTO + Mapper 생성 시 클래스 140개 이상 발생  
- 스키마 변경 시 코드 수정 비용 증가  

**Decision**  
- `ColumnMapRowMapper` 기반의 **Generic Map 구조** 채택  

**Benefit**  
- 런타임 메타데이터 기반 동적 처리  
- 스키마 변경 대응 유연성 확보  
- 코드량 및 개발 공수 **90% 이상 절감**  

---

### 2. Auto-Discovery (운영 자동화)

**Problem**  
- YAML 기반 테이블 관리 → 누락 및 휴먼 에러 발생 가능  

**Decision**  
- `JDBC DatabaseMetaData` 활용  
- 특정 스키마(KDM)의 모든 테이블 자동 탐색  

**Benefit**  
- 설정 파일 없이 전체 테이블 자동 처리  
- 완전 자동화된 이관 파이프라인 구축  

---

### 3. Parallel Processing (처리량 극대화)

**Problem**  
- 140개 테이블을 단일 스레드로 처리 시 수행 시간 급증  

**Decision**  
- Spring Batch `Flow + Split` 구조 기반 병렬 처리  

**Benefit**  
- CPU 및 Connection Pool 자원 최대 활용  
- 전체 처리 시간 단축 (Throughput 향상)  

---

## 트러블슈팅 및 해결 (Troubleshooting)

### 1. Spring Batch 빈 생명주기 문제

**Issue**  
- 루프 내 Reader 직접 생성 시 `JdbcTemplate` 초기화 실패  
- `NullPointerException` 발생  

**Solution**  
- `afterPropertiesSet()` 수동 호출로 초기화 강제 수행  

---

### 2. Unique Constraint 불일치

**Issue**  
- PostgreSQL에 Unique Index 없는 상태에서  
  `ON CONFLICT` 사용 → 문법 오류 발생  

**Solution**  
- Paging → Cursor 방식 (`JdbcCursorItemReader`) 전환  
- `ON CONFLICT` 제거 후 순수 INSERT 구조로 변경  

---

### 3. 네트워크 불안정 (Connection Reset)

**Issue**  
- 대량 데이터 전송 중 커넥션 강제 종료 발생  

**Solution**  
- HikariCP `keepalive-time` 설정 적용  
- 주기적 헬스체크로 유휴 연결 유지  

---

### 4. 데이터 정합성 & 재시작 전략

**Risk**  
- Cursor 방식은 정렬 키가 없어  
  재시작 시 중복 데이터 발생 가능  

**Countermeasure**

1. **Idempotency 확보**
   - 실패 시 테이블 `TRUNCATE` 후 재적재 (Clean-Start)

2. **메타데이터 기반 모니터링**
   - `BATCH_STEP_EXECUTION` 테이블 활용
   - 실패 지점 추적 및 수동 보정 프로세스 정의  

**Result**  
- 다양한 제약 조건 환경에서도  
- 복잡도를 낮추고 운영 명확성을 선택  
- **이관 성공률 100% 달성**

---

## 학습 및 회고 (Retrospective)

### Batch 아키텍처
- Paging vs Cursor 방식 비교  
  - 정렬 키 필요 여부  
  - 메모리 사용량  
  - 재시작 전략 차이  

### Spring JDBC
- `NamedParameterJdbcTemplate` 활용  
- 동적 SQL 생성 및 Batch Update 최적화  

### 병렬성 제어
- `TaskExecutor` 기반 스레드 풀 관리  
- DB Connection Pool과의 상관관계 이해  

---

## 핵심 요약

- DTO-less 구조로 **확장성과 유지보수성 확보**
- Auto-Discovery로 **완전 자동화된 이관 시스템 구축**
- 병렬 처리로 **대용량 데이터 처리 성능 극대화**
- 운영 중심 전략으로 **안정적인 100% 이관 성공 달성**