# Spring-Boot-Github-Actions-CI-CD
### Github-Actions와 Docker, Docker-Compose를 이용한 Spring Boot Project의 CI/CD 환경 구축

# OverView

## Github Actions
> Automate, customize, and execute your software development workflows right in your repository with GitHub Actions. You can discover, create, and share actions to perform any job you’d like, including CI/CD, and combine actions in a completely customized workflow.

Github Repository와 직접 연동해서 사용할 수 있는 CI/CD 도구입니다. 개발 후 배포 과정을 자동화, 커스텀, 실행할 수 있습니다.


## Environment
- Github Actions
- Spring Boot (using Prd, Dev)
- AWS EC2 (using Prd, Dev)
- Docker, Docker Compose
- Gradle

- - -

# AWS EC2 인스턴스 설정
## 인스턴스 생성
환경 구성을 위해 [AWS EC2](https://aws.amazon.com/ko/?nc2=h_lg) 인스턴스를 생성해보겠습니다. 프리티어 사양으로 생성하고, 스토리지는 프리티어 최대용량인 30GB로 설정하였습니다.

보안그룹에는 인바운드 그룹에 8080포트를 허용하였습니다.

운영환경(Prd), 개발환경(Dev) 환경으로 2개의 인스턴스를 생성합니다.

![스크린샷 2022-09-13 18 44 47](https://user-images.githubusercontent.com/79642391/189869315-24895178-b567-4cc2-815d-40fce67b554a.png)

![스크린샷 2022-09-13 18 45 13](https://user-images.githubusercontent.com/79642391/189869484-90ed2d65-e99d-4780-a7a9-2d23427a066d.png)

**[주의] 키페어는 발급받으면 반드시 보관하고, 분실되지 않도록 주의합니다!**

![스크린샷 2022-09-13 18 45 21](https://user-images.githubusercontent.com/79642391/189869497-46c88ffb-a8c9-4bce-938b-cbd2e25ddeae.png)

![스크린샷 2022-09-13 18 45 30](https://user-images.githubusercontent.com/79642391/189869512-d0652bcf-9119-405d-a0f7-a3aa4256ab9d.png)

생성을 완료하고 목록에서 dev,prd 인스턴스 2개가 정상적으로 시작된것을 확인할 수 있습니다.
![스크린샷 2022-09-13 19 22 24](https://user-images.githubusercontent.com/79642391/189877570-a549ddce-f93e-4312-a921-4b9f7098a131.png)

## 인스턴스에 도커 설치
EC2 인스턴스에 배포에 필요한 Docker, Docker Compose를 설치합니다.
```
// Docker 설치
sudo yum install docker -y

// Docker 실행
sudo service docker start

// Docker 상태 확인
systemctl status docker.service

// Docker 관련 권한 추가
sudo chmod 666 /var/run/docker.sock
docker ps

// Docker Compose 설치
sudo curl \
-L "https://github.com/docker/compose/releases/download/1.26.2/docker-compose-$(uname -s)-$(uname -m)" \
-o /usr/local/bin/docker-compose

// Docker Compose 권한 추가
sudo chmod +x /usr/local/bin/docker-compose

// Docker Compose 버전 확인
docker-compose --version
```

# Github Actions 설정
## 스크립트 파일 생성
이번에는 CI/CD 작업에 사용할 Github Actions 스크립트 파일을 생성하겠습니다.

![스크린샷 2022-09-13 19 26 04](https://user-images.githubusercontent.com/79642391/189878570-0138f8c4-9257-4513-b3e7-91aa0ad18de0.png)

Github Repository - Actions - Get started with GitHub Actions에서 Java with Gradle의 Configure를 클릭합니다.

`~/.github/workflow/gradle.yml` 라는 파일이 생성되는데, 이곳에 스크립트를 작성할 수 있습니다.

`gradle.yml`의 파일명은 자유롭게 작성해도 괜찮습니다.

## 스크립트 작성
아래 스크립트는 제가 적용한 스크립트입니다. 자세한 설명은 아래에서 진행하겠습니다.

Github Repository에 main branch(prd), dev branch(dev)로 push가 될 때, main branch에 pull request가 발생할 때, 각 환경별로 분기해 Docker Build한 뒤 AWS EC2 인스턴스에 배포가 됩니다.

```
name: Java CI with Gradle

on:
  push:
    branches: [ "main", "develop" ]
  pull_request:
    branches: [ "main" ]

permissions:
  contents: read

jobs:
  CI-CD:
    runs-on: ubuntu-latest
    steps:
    
    ## jdk setting
    - uses: actions/checkout@v3
    - name: Set up JDK 11
      uses: actions/setup-java@v3
      with:
        java-version: '11'
        distribution: 'temurin'
        
    ## gradle caching
    - name: Gradle Caching
      uses: actions/cache@v3
      with:
        path: |
          ~/.gradle/caches
          ~/.gradle/wrapper
        key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
        restore-keys: |
          ${{ runner.os }}-gradle-
          
    ## create application-dev.properties
    - name: make application-dev.properties
      if: contains(github.ref, 'develop')
      run: |
        mkdir ./src/main/resources
        cd ./src/main/resources
        touch ./application-dev.properties
        echo "${{ secrets.PROPERTIES_DEV }}" > ./application-dev.properties
      shell: bash

    ## create application-prd.properties
    - name: make application-prd.properties
      if: contains(github.ref, 'main') # main branch일때
      run: |
          mkdir ./src/main/resources
          cd ./src/main/resources
          touch ./application-prd.properties
          echo "${{ secrets.PROPERTIES_PRD }}" > ./application-prd.properties
      shell: bash
      
    ## gradle build
    - name: Build with Gradle
      run: ./gradlew build -x test
      
      
    ## docker build & push to production
    - name: Docker build & push to prd
      if: contains(github.ref, 'main')
      run: |
          docker login -u ${{ secrets.DOCKER_USERNAME }} -p ${{ secrets.DOCKER_PASSWORD }}
          docker build -f Dockerfile-prd -t ${{ secrets.DOCKER_REPO }}/cicd-prd .
          docker push ${{ secrets.DOCKER_REPO }}/cicd-prd

    ## docker build & push to develop
    - name: Docker build & push to dev
      if: contains(github.ref, 'develop')
      run: |
          docker login -u ${{ secrets.DOCKER_USERNAME }} -p ${{ secrets.DOCKER_PASSWORD }}
          docker build -f Dockerfile-dev -t ${{ secrets.DOCKER_REPO }}/cicd-dev .
          docker push ${{ secrets.DOCKER_REPO }}/cicd-dev

    ## deploy to production
    - name: Deploy to prd
      uses: appleboy/ssh-action@master
      id: deploy-prd
      if: contains(github.ref, 'main')
      with:
          host: ${{ secrets.HOST_PRD }}
          username: ec2-user
          key: ${{ secrets.PRIVATE_KEY }}
          envs: GITHUB_SHA
          script: |
              var=$(docker ps -aq) | [ -z "$var" ] && echo "no running containers" || sudo docker rm -f $var
              sudo docker pull ${{ secrets.DOCKER_REPO }}/cicd-prd
              docker-compose up -d
              docker image prune -f

    ## deploy to develop
    - name: Deploy to dev
      uses: appleboy/ssh-action@master
      id: deploy-dev
      if: contains(github.ref, 'develop')
      with:
        host: ${{ secrets.HOST_DEV }}
        username: ${{ secrets.USERNAME }}
        password: ${{ secrets.PASSWORD }}
        port: 22
        #key: ${{ secrets.PRIVATE_KEY }}
        script: |
            var=$(docker ps -aq) | [ -z "$var" ] && echo "no running containers" || sudo docker rm -f $var
            sudo docker pull ${{ secrets.DOCKER_REPO }}/cicd-dev
            docker-compose up -d
            docker image prune -f
            
    ## time
  current-time:
      needs: CI-CD
      runs-on: ubuntu-latest
      steps:
        - name: Get Current Time
          uses: 1466587594/get-current-time@v2
          id: current-time
          with:
            format: YYYY-MM-DDTHH:mm:ss
            utcOffset: "+09:00"

        - name: Print Current Time
          run: echo "Current Time=${{steps.current-time.outputs.formattedTime}}"
          shell: bash
```

## 각 구문별 설명

```
name: Java CI with Gradle

on:
  push:
    branches: [ "main", "develop" ]
  pull_request:
    branches: [ "main" ]
```
`name`은 Github repository Actions에 나타낼 이름을 설정합니다.

`on - push or pull_request` : main, develop 브랜치에 push가 일어날 때, main 브랜치에 pull_request가 일어날 때 Workflow를 trigger시킨다는 의미입니다.

- - - 

```
permissions:
  contents: read
```
`permissions`을 사용하여 GITHUB_TOKEN에 부여된 기본 사용 권한을 수정하고 필요에 따라 액세스를 추가 또는 제거하여 필요한 최소한의 액세스만 허용할 수 있습니다. 자세한 내용은 [워크플로우에서 인증확인](https://docs.github.com/en/actions/security-guides/automatic-token-authentication#permissions-for-the-github_token)에 나와있습니다.


`permissions`을 최상위 키로 사용하거나 워크플로에 있는 모든 작업에 적용하거나 특정 작업 내에서 적용할 수 있습니다. 특정 작업 내에서 `permissions` 키를 추가하면 해당 작업 내에서 GITHUB_TOKEN을 사용하는 모든 작업 및 실행 명령이 사용자가 지정한 액세스 권한을 얻습니다.자세한 내용은 [jobs.<job_id>.permissions](https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions#jobsjob_idpermissions)에 있습니다.

- - -

다음은 Jobs 구성입니다.
Github Actions는 여러개의 Jobs로 구성되고, Job 안에서 다시 여러개의 steps로 구성됩니다.

```
jobs:
  CI-CD:
    runs-on: ubuntu-latest
    steps:
    
    ## jdk setting
    - uses: actions/checkout@v3
    - name: Set up JDK 11
      uses: actions/setup-java@v3
      with:
        java-version: '11'
        distribution: 'temurin'
```

먼저 Github Actions에서 사용할 JDK를 설정합니다. (프로젝트나 AWS의 Java 버전과 달라도 됩니다.)

JDK 버전 11을 사용하고, distribution으로 `temurin`을 사용합니다. (`adopt`는 더이상 업데이트 되지 않기에 `temurin`를 권장하고 있습니다. [참조](https://github.com/actions/setup-java))

- - -

```
## gradle caching
    - name: Gradle Caching
      uses: actions/cache@v3
      with:
        path: |
          ~/.gradle/caches
          ~/.gradle/wrapper
        key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
        restore-keys: |
          ${{ runner.os }}-gradle-
```

다음은 Gradle Caching 작업입니다. 

이 부분은 적용하지 않아도 무방하나 빌드시간이 단축되기에 추가하였습니다. 자세한 설명은 [이곳](https://github.com/actions/cache)을 참조해주세요

- - -

```
## create application-dev.properties
    - name: make application-dev.properties
      if: contains(github.ref, 'develop')
      run: |
        mkdir ./src/main/resources ## ./src/main/resources 폴더 생성
        cd ./src/main/resources ## ./src/main/resources 폴더로 이동
        touch ./application-dev.properties ## application-dev.properties 파일 생성
        echo "${{ secrets.PROPERTIES_DEV }}" > ./application-dev.properties ## github actions secret에 있는 PROPERTIES_DEV 값을 application-dev.properties에 주입
      shell: bash

    ## create application-prd.properties
    - name: make application-prd.properties
      if: contains(github.ref, 'main') # main branch일때 위 dev 환경과 동일
      run: |
          mkdir ./src/main/resources
          cd ./src/main/resources
          touch ./application-prd.properties
          echo "${{ secrets.PROPERTIES_PRD }}" > ./application-prd.properties
      shell: bash
```

application-dev.properties, application-prd.properties를 생성해주는 부분입니다.

이 파일에는 Private한 정보(ex, DB 접속정보, AWS 접속정보, JWT관련 Secret 등)가 포함되기에 Github Repository에 올리는 것이 아닌, Github Actions Secret에서 값을 읽어와 주입, 생성합니다.

Secret 정보는 Settings > Secrets > Actions > New repository secret 에서 생성할 수 있습니다.

**Secret 정보는 한번 생성하면 다시는 값을 확인할 수 없습니다. 오직 업데이트만 가능합니다.**

![스크린샷 2022-09-13 19 56 25](https://user-images.githubusercontent.com/79642391/189883857-1497cce3-3fbb-45a8-ab9d-9fbd0ca1af89.png)

- `DOCKER_PASSWORD` : 도커 계정 패스워드
- `DOCKER_REPO` : 도커 레포지토리
- `DOCKER_USERNAME` : 도커 계정 아이디
- `HOST_DEV` : AWS EC2 dev 환경의 인스턴스 ip (퍼블릭 IPv4 DNS)
- `HOST_PRD` : AWS EC2 prd 환경의 인스턴스 ip (퍼블릭 IPv4 DNS)
- `PASSWORD` : AWS EC2 prd 환경의 인스턴스 패스워드
- `PRIVATE_KEY` : AWS EC2 prd 환경에 github actions ssh-actions가 접근하게 해주는 rsa key
- `PROPERTIES_DEV` : application-dev.properties 파일 내용
- `PROPERTIES_PRD` : application-prod.properties 파일 내용
- `USERNAME` : AWS EC2 인스턴스 계정 ID(ec2-user)

- - - 

```
## gradle build
    - name: Build with Gradle
      run: ./gradlew build -x test
      
      
    ## docker build & push to production
    - name: Docker build & push to prd
      if: contains(github.ref, 'main')
      run: |
          docker login -u ${{ secrets.DOCKER_USERNAME }} -p ${{ secrets.DOCKER_PASSWORD }}
          docker build -f Dockerfile-prd -t ${{ secrets.DOCKER_REPO }}/cicd-prd .
          docker push ${{ secrets.DOCKER_REPO }}/cicd-prd

    ## docker build & push to develop
    - name: Docker build & push to dev
      if: contains(github.ref, 'develop')
      run: |
          docker login -u ${{ secrets.DOCKER_USERNAME }} -p ${{ secrets.DOCKER_PASSWORD }}
          docker build -f Dockerfile-dev -t ${{ secrets.DOCKER_REPO }}/cicd-dev .
          docker push ${{ secrets.DOCKER_REPO }}/cicd-dev
```

다음은 Gradle Build 및 Docker Build & Push 과정입니다.

여기서 Gradle Build의 Buiild는 `-x test`만 설정하여 테스트를 진행하지 않는 빌드로 설정하였습니다.

Dockerfile은 prd,dev별로 spring profiles를 분리해야 하기 때문에 2개로 분리하였습니다. Dockerfile은 프로젝트 최상위 루트에 작성하면 됩니다.

![스크린샷 2022-09-13 20 03 56](https://user-images.githubusercontent.com/79642391/189885352-06f6d964-85c6-4d79-82b5-27abb35f5fcc.png)

```
## Dockerfile-dev
FROM openjdk:8
EXPOSE 8080
ARG JAR_FILE=/build/libs/cicd-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","-Dspring.profiles.active=dev","/app.jar"]

## Dockerfile-prd
FROM openjdk:8
EXPOSE 8080
ARG JAR_FILE=/build/libs/cicd-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","-Dspring.profiles.active=prd","/app.jar"]
```

- - - 

```
## deploy to production
    - name: Deploy to prd
      uses: appleboy/ssh-action@master
      id: deploy-prd
      if: contains(github.ref, 'main')
      with:
          host: ${{ secrets.HOST_PRD }}
          username: ec2-user
          key: ${{ secrets.PRIVATE_KEY }}
          envs: GITHUB_SHA
          script: |
              var=$(docker ps -aq) | [ -z "$var" ] && echo "no running containers" || sudo docker rm -f $var
              sudo docker pull ${{ secrets.DOCKER_REPO }}/cicd-prd
              docker-compose up -d
              docker image prune -f

    ## deploy to develop
    - name: Deploy to dev
      uses: appleboy/ssh-action@master
      id: deploy-dev
      if: contains(github.ref, 'develop')
      with:
        host: ${{ secrets.HOST_DEV }}
        username: ${{ secrets.USERNAME }}
        password: ${{ secrets.PASSWORD }}
        port: 22
        #key: ${{ secrets.PRIVATE_KEY }}
        script: |
            var=$(docker ps -aq) | [ -z "$var" ] && echo "no running containers" || sudo docker rm -f $var
            sudo docker pull ${{ secrets.DOCKER_REPO }}/cicd-dev
            docker-compose up -d
            docker image prune -f
```

이제 SSH 연결을 통해 AWS EC2 인스턴스에 Docker의 이미지를 가져와 배포하는 설정입니다.

`secrets.HOST_PRD`, `secrets.HOST_DEV`는 AWS EC2 인스턴스의 `퍼블릭 IPv4 DNS` 입니다.

`secrets.USERNAME`, `secrets.PASSWORD` 의 경우 AWS EC2 SSH에 접속해서 설정할 수 있습니다.

```
// ec2-user 계정 비밀번호 설정
sudo passwd ec2-user

// sshd_config 의 PasswordAuthentication를 no에서 yes로 변경
sudo vi /etc/ssh/sshd_config
-> PasswordAuthentication yes

// sshd 서비스 재시작
sudo service sshd restart

// ec2 인스턴스 로그인
ssh ec2-user@[EC2 인스턴스 ip]
```

`secrets.PRIVATE_KEY`는 AWS EC2 prd 환경 SSH에 접속한 뒤, SSH 키를 생성합니다.

### Step 1. SSH Key 생성
서버에 접속한 후 `.ssh` 폴더로 이동하여 SSH 키를 생성합니다.
```
cd ~/.ssh
```

```
ssh-keygen -t rsa -b 4096 -C "your_email@example.com"
```
명령어를 입력하면 SSH 키 파일의 이름을 지정해야 합니다. 여기서 기본 파일 이름(id_rsa) 을 사용하는 것을 권장하지 않습니다. 이 키가 Github Actions에 사용된다는 것을 알 수 있도록 github-actions와 같은 이름으로 바꾸는 것을 권장합니다. 6개월 후에 SSH 키를 볼 때 어떤 키인지 인지할 수 있습니다.

암호도 입력해야 하지만 Github에서 SSH 명령을 실행할 때 암호를 입력할 수 없으므로 비워 둡니다.

### Step 2. 공인 키에 공개 키 추가
개인 키 github-actions.pub 를 사용하는 기기가 서버에 접근할 수 있도록 authorized_keys를 github-actions 에 추가해야 합니다.

```
cat github-actions.pub >> authorized_keys
```

### Step 3. Github Secret에 키 추가
```
nano github-actions 
```
매우 긴 난수들이 나오는데 모두 복사하여 PRIVATE_KEY에 키를 추가합니다.

- - - 

시간 출력

```
## time
  current-time:
      needs: CI-CD
      runs-on: ubuntu-latest
      steps:
        - name: Get Current Time
          uses: 1466587594/get-current-time@v2
          id: current-time
          with:
            format: YYYY-MM-DDTHH:mm:ss
            utcOffset: "+09:00"

        - name: Print Current Time
          run: echo "Current Time=${{steps.current-time.outputs.formattedTime}}"
          shell: bash
```
현재 시간을 출력합니다. (필수 아님)

- - -

다음으로 EC2 인스턴스에 `docker-compose.yaml` 파일을 생성합니다.

```
## dev
version: "3"
services:
  cicd:
    image: ksh117/cicd-dev
    restart: always
    container_name: cicd-dev
    ports:
      - 8080:8080
      
## prd
version: "3"
services:
  cicd:
    image: ksh117/cicd-prd
    restart: always
    container_name: cicd-prd
    ports:
      - 8080:8080
```

- - - 

이제 각 branch별로 push를 하면 Github Actions가 스크립트를 토대로 빌드-배포를 진행합니다.
![스크린샷 2022-09-13 20 23 14](https://user-images.githubusercontent.com/79642391/189888801-38928ef4-505b-49a0-8236-bf523b213c1a.png)



https://docs.github.com/en/actions

https://github.com/actions/cache
