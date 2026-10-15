# LLM-based Online Judgement System for University Courses(Linux Course as an Example)

An online lab platform for a Linux course. Teachers manage a question bank, publish experiments to a class, and review scores. Students complete experiments in the browser. Submissions are graded automatically by an LLM, with teacher review and a dispute flow when a score needs to be corrected.

The app has two portals: `/teacher` and `/student`. Access is by role. There is no public sign-up; student accounts are created when a teacher imports a roster.

**[Watch the demo](https://youtu.be/7ACd5P9unc4?si=om107xzGp6A66eXT)**

[![Video demo](https://img.youtube.com/vi/7ACd5P9unc4/hqdefault.jpg)](https://youtu.be/7ACd5P9unc4?si=yR6aLWJ6Cudya-0_)

## Features

### Accounts

- Log in and log out with a JWT
- Teacher and student route guards
- Login lockout after repeated failed attempts
- View profile and change password
- Teachers import a student roster from Excel (preview, then confirm), add a student, edit an enrollment, reset a password, and unenroll

### Question bank (teacher portal)

- Create a question and add it to the question bank
- Update a question in the question bank
- Delete a question from the question bank
- Question types: shell script, C program, and Q&A
- Each question is a set of sub-questions answered as text, an image, or code (Bash or C)
- Markdown content with image upload, a reference answer, and a scoring rubric

### Experiment

- Create an experiment by adding questions from the bank, scoped to a class
- Publish an experiment, withdraw it, or let it close at the deadline
- Students view the experiment, save a draft, and confirm the submission

### Judgement

- LLM-based automatic assessment of submissions
- Text answers are scored directly; image answers are read with OCR first; code answers can run in a Docker sandbox, and the program output is included in the grade
- Manual assessment by the teacher
- Students open disputes and can withdraw them; teachers resolve a dispute or send it back to the model

### Scores (teacher portal)

- View scores by class or by experiment
- Export scores to Excel

### Language

- Interface and API messages in Simplified Chinese and English
- The frontend language switcher sends `Accept-Language`; the backend uses that header for its messages

## How grading works

1. A confirmed submission is published to a RabbitMQ queue.
2. A worker in the same Spring Boot app claims the submission.
3. Each sub-question is graded by an LLM (LangChain4j with DashScope Qwen). Image answers go through Qwen-VL OCR. Code answers (Bash or C) can execute in Docker (`sandbox-bash`, `sandbox-c`); the model scores the source together with compile output and stdout/stderr, and does not run the code itself.
4. The result is a score and a short reason. A teacher can override that result from the dispute pages.

`app.grading.scorer` can be `llm` or `fake`. `app.grading.sandbox` can be `docker` or `noop`.

## Tech stack

### Frontend

- React
- TypeScript
- Vite
- Tailwind CSS
- shadcn/ui
- React Router
- i18next

### Backend

- Java 21
- Spring Boot 3.5.16
- Spring Security (JWT)
- Lombok
- MySQL
- MyBatis-Plus 3.5.14
- RabbitMQ
- LangChain4j
- Docker
- EasyExcel 4.0.3
- Tencent COS (optional file storage; local disk is the other option)

## Project layout

| Path | Description |
| --- | --- |
| [`frontend/`](frontend/) | React client (Vite). Dev server proxies `/api` to the backend. |
| [`backend/`](backend/) | Spring Boot API (`linuxback`), grading worker, and persistence. |
| [`docs/`](docs/) | MySQL schema dump, [`llm-judge-online-dump-en.sql`](docs/llm-judge-online-dump-en.sql). |

## Getting started

### Prerequisites

- Node.js (for the frontend)
- Java 21 and Maven (for the backend)
- MySQL
- RabbitMQ
- Docker, when sandbox grading is enabled
- A DashScope API key, when `app.grading.scorer` is `llm`

Create the database and load the schema in [`docs/llm-judge-online-dump-en.sql`](docs/llm-judge-online-dump-en.sql) before starting the API. The file is a table-structure dump for `linux_judge_system` (it drops and recreates tables; it does not insert rows):

```sql
CREATE DATABASE linux_judge_system
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

```bash
mysql -u root -p linux_judge_system < docs/llm-judge-online-dump-en.sql
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server proxies `/api` to `http://localhost:8080`.

### Backend

Configure the datasource, RabbitMQ, and optional LLM / object-storage settings in [`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml), then start the API:

```bash
cd backend
mvn spring-boot:run
```

The API listens on port `8080` with context path `/api`.

These environment variables override the defaults in `application.yml`:

| Variable | Used for |
| --- | --- |
| `JWT_SECRET` | Signing login tokens |
| `RABBITMQ_ENABLED`, `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST` | Grading queue |
| `DASHSCOPE_API_KEY` | LLM grading and OCR |
| `GRADING_SCORER` | `llm` or `fake` |
| `GRADING_SANDBOX` | `docker` or `noop` |
| `COS_SECRET_ID`, `COS_SECRET_KEY` | Tencent COS, when `app.storage.type` is `tencent-cos` |

### Tests

```bash
cd frontend
npm test

cd backend
mvn test
```



### How to deploy in VPS:

##### Frontend:

1.configure the vite.config.ts, and set the target to the ip/domain of your VPS:

 ```bash
 server: {
   proxy: {
     "/api": {
       target: "ip/domain of your vps",
       changeOrigin: true,
     },
   },
 },
 ```

2.Production build:

```bash
npm install
npm run build
```

3.Upload the outcome in dist/ to the VPS;

4.Add configuration to Nginx Server:
```bash
    location /api {
      proxy_pass http://127.0.0.1:8080;
      proxy_set_header Host $proxy_host;
      proxy_set_header X-Real-IP $remote_addr;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
      proxy_buffering off;
      proxy_set_header Connection "";
    }
    
    location / {
      try_files $uri $uri/index.html /index.html;
    }
```

##### Backend:

1.Install Mysql 5.7 in VPS, upload sql schema file and then import into the Mysql;

Set the name of the database, suppose it's "linux_judge_system", and then the password.

Both the name and password will be used in Java spring configuration file.



2.Instal RabbitMQ：

```bash
#check whether it's running:
systemctl status rabbitmq-server --no-pager -l

#autostart when system reboots;
sudo systemctl enable rabbitmq-server
```



3.Java Spring Production Configuration(application-prod.yml)：

```yaml
spring:
  application:
    name: linuxback

  autoconfigure:
    exclude: org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration

  datasource:
    url: jdbc:mysql://localhost:3306/linux_judge_system?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: linux_judge_system # the name of your database, be consistent with the above mysql url;
    password: ****** #the password of your database;
    driver-class-name: com.mysql.cj.jdbc.Driver

  jackson:
    serialization:
      write-dates-as-timestamps: false
    time-zone: Asia/Shanghai

  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 10MB

  messages:
    basename: i18n/messages
    encoding: UTF-8
    fallback-to-system-locale: false
    use-code-as-default-message: false

server:
  port: 8080 #the port for backend
  servlet:
    context-path: /api

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: false   # question/sub_question/attachment columns are already camelCase
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # log SQL to stdout during development
  global-config:
    db-config:
      logic-delete-field: isDelete
      logic-delete-value: 1
      logic-not-delete-value: 0

app:
  jwt:
    secret: ${JWT_SECRET:replace-this-with-your-own-secret-32b+} #JWT_SECRET
    ttl-hours: 8
  rabbitmq:
    enabled: ${RABBITMQ_ENABLED:true}
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:guest}
    password: ${RABBITMQ_PASSWORD:guest}
    virtual-host: ${RABBITMQ_VHOST:/}
    grading-queue: submission-grading-queue #name of your queue
    grading-dlq: submission-grading-queue.dlq
    grading-dlx: submission-grading-dlx
  grading:
    scorer: ${GRADING_SCORER:llm}
    #scorer: {GRADING_SCORER:fake}
    #sandbox: ${GRADING_SANDBOX:noop}
    sandbox: ${GRADING_SANDBOX:docker}
    docker:
      binary: docker
      timeout-secs: 10
      compile-timeout-secs: 10
      wait-slack-secs: 15
      memory: 128m
      pids-limit: 64
      cpus: "1"
      tmpfs-size: 16m
      images:
        bash: sandbox-bash:latest
        c: sandbox-c:latest
    republish-delay-ms: 300000
    republish-age-minutes: 10
    claim-timeout-minutes: 20
    claim-timeout-delay-ms: 300000
    fake-score-delay-min-ms: 1000
    fake-score-delay-max-ms: 5001
    llm:
      api-key: ${DASHSCOPE_API_KEY:} #input your api-key here, currently supports qwen 
      model: qwen-plus
      timeout-ms: 60000
    ocr:
      model: qwen-vl-ocr
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      timeout-ms: 60000
  storage:
    type: tencent-cos   # local | tencent-cos 
    local:
      base-path: ./uploads
      url-prefix: /files
    tencent-cos:
      secret-id: ${COS_SECRET_ID:input your id here}
      secret-key: ${COS_SECRET_KEY:input your key here}
      region: ap-shanghai
      bucket: input your bucketid here
```

4.build your Java spring project and generate the jar, upload the jar to the VPS and run:

```bash
java -jar  -Xmx1024M -Xms256M /www/wwwroot/linuxback/linuxback-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

