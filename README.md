# Week 11 — Attachment Board (Student Sample)

Spring Boot + Thymeleaf + JPA + MariaDB: 게시글 CRUD / 다중 첨부파일 업로드 / 다운로드

## 실행하기
1. Java 17+ 및 MariaDB 준비, `CREATE DATABASE web11 CHARACTER SET utf8mb4;`
2. 환경변수 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 자신의 MariaDB에 맞게 설정. 기본값은 `jdbc:mariadb://localhost:3306/web11`, `webuser`, `webpass`.
3. Gradle이 설치된 환경: `gradle bootRun` / IntelliJ: `AttachmentBoardApplication.main()` 실행.
4. 접속: http://localhost:8080/posts

> Gradle Wrapper는 이 ZIP에 포함되어 있지 않습니다. IntelliJ의 Gradle 지원 또는 설치된 Gradle을 사용하세요. `gradle wrapper` 실행 후 생성된 wrapper 파일은 Git에 포함하는 것을 권장합니다.

## 구현된 기능
- 게시글 목록 / 생성 / 상세 / 수정 / 삭제
- 게시글 생성 시 여러 파일 선택, UUID 이름으로 `uploads/`에 저장
- `attachments` 테이블에 메타데이터 저장
- 첨부파일 다운로드 (`GET /attachments/{id}/download`)

## 학생 과제 (TODO)
1. 코드를 분석하여 업로드/다운로드 요청 흐름 정리
2. 화면이나 정보 표시 3곳 이상 수정
3. **이미지 미리보기** 구현: `GET /attachments/{id}/view` + 상세 화면 `<img>` 표시. 이미지(jpg/jpeg/png/gif)만 허용하고 `Content-Type` 정확히 설정
4. 정상/오류 테스트 후 README 및 Weekly Review 작성

## 코드 위치
- `PostController`: 게시글 화면 / Form
- `AttachmentController`: 다운로드 + 미리보기 TODO
- `PostService`: JPA CRUD와 파일 저장 연결
- `FileStorageService`: 파일 검증 / UUID 저장 / 로드
- `Post`, `Attachment`: 1:N 관계
- `templates/posts/detail.html`: 미리보기 TODO

## 주의사항
- 학습용 프로젝트입니다. 로그인/접근 권한, 업로드 파일의 실제 바이트 검증, 악성 파일 검사, CSRF 보호, 파일 저장과 DB 트랜잭션의 완전한 일관성 보장 등은 구현하지 않았습니다. 인터넷 공개 배포 전에 보강이 필요합니다.
- `spring.jpa.hibernate.ddl-auto=update`는 학습용 설정이며 운영 DB에는 그대로 사용하지 않습니다.
- Render 기본 파일 시스템에 저장된 업로드 파일은 재시작/재배포 시 유지되지 않을 수 있습니다. 이번 과제는 **로컬 파일 처리**를 평가합니다.

Project Structure & Spring MVC
1. PostController, PostService, PostRepository는 각각 어떤 역할을 하며, 서로 어떻게 연결되는가?
PostController는 게시글 http요청을 받고 postService를 호출한 뒤 이동할 주소를 반환한다. PostService는 게시글을 처리하거나 파일을 저장하는 것을 연결하며 PostRepository는 JPA를 통해 게시글 데이터를 db에 저장한다.
게시글을 작성할 때 PostController.create() -> PostService.create() -> Repository의 저장 순서로 이어진다.

2. 게시글 작성 시 HTML Form의 입력값과 첨부파일은 Controller에 어떻게 전달되는가?
게시글 작성 폼은 multipart/form-data 형식으로 입력값, 파일을 같이 전송한다. 제목이나 내용은 @ModelAttribute("postForm")을 통해서 PostForm에 연결되고 첨부파일은 @RequestParma(name="files) 로 List<MultipartFile>로 전달되게된다.

JPA & Entity Relationship
1. Post와 Attachment Entity는 어떤 관계이며, @ManyToOne과 @OneToMany는 각각 어떤 역할을 하는가?
게시글 하나에 첨부파일이 여러개 연결될 수 있기 때문에 post,attachment는 일대다 관계이다. @OneToMany는 게시글에 속한 첨부파일 목록이며 @ManyToOne은 각각의 첨부파일이 속한 게시물이다. 
2. cascade = CascadeType.ALL과 orphanRemoval = true는 어떤 역할을 하며, 실제 디스크에 저장된 파일에도 영향을 주는가?
CascadeType.ALL은 부모 Entity에 수행하는 저장이나 삭제 등의 작업들을 연결된 자식 Entity에 전파한다. 
orphanRemoval = true는 부모와의 관계에서 제거된 자식 Entity를 DB에서도 삭제하도록 한다. 두 설정이 JPA가 관리하는 Entity에 적용되므로 디스크의 파일까지 자동으로 삭제하지 않고 실제 파일 삭제에는 FileStorageService.deleteIfExists() 같이 별도적인 처리가 필요하다.

File Upload
1. MultipartFile은 무엇이며, 업로드된 파일은 어떤 과정을 거쳐 서버에 저장되는가?
MultipartFile은 multipart 요청으로 전달된 파일의 이름이나 크기, 내용 등을 다룰 수 있는 Spring 인터페이스이다. 
업로드된 파일은 PostController.create()에서 받아서 Service로 전달한다 FileStorageService.store()에서 파일 이름과 확장자랑 크기를 검증한다. 
검증을 통과하면 UUID 기반 저장 이름과 경로를 만들고 Files.copy()로 디스크에 저장한 후에 생성된 Attachment의 메타데이터는 게시글과 연결되어 DB에 저장된다.
2. 파일을 원본 이름 대신 UUID 기반 파일명으로 저장하는 이유는 무엇인가?
원본 파일명으로 저장하면 서로 다른 사용자가 같은 이름의 파일을 올렸을 때 기존 파일을 덮어쓸 수 있다. UUID 기반 이름은 이러한 이름 충돌 가능성을 매우 낮추고, 서버가 저장 이름을 직접 정할 수 있게 한다.

File Download & Security
1. 파일 다운로드 시 Resource, ResponseEntity, Content-Disposition은 각각 어떤 역할을 하는가?
Resource는 다운로드할 실제 파일을 Spring에서 다룰 수 있도록 표현한다.
ResponseEntity는 파일을 응답 본문으로 담고 HTTP 상태와 헤더를 설정하는 역할을 한다. 
Content-Disposition은 응답을 첨부파일로 다운로드하도록 안내하고, 다운로드할 때 사용할 원본 파일명을 지정한다.
2. 파일 저장 과정에서 normalize()와 startsWith()를 사용하는 이유는 무엇인가?
normalize()는 경로에 포함된 .이나 .. 같은 요소를 정리하여 경로를 비교하기 쉽게 만든다. 이후 startsWith(uploadDir)로 저장 대상 경로가 업로드 폴더 안에 있는지 검사한다.