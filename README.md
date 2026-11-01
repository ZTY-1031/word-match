# 接词挑战 · Word Match

两个人各自写下一个词，**看不见对方写什么**；两边都提交后同时揭晓，**词一样就算赢**。
不一样的两个词会成为下一轮的锚点，继续往下接，直到撞上，或者撞满回合上限。

- 前端：Vue 3 + Vite + 原生 WebSocket（单页，移动端优先）
- 后端：Spring Boot 3.3 + 原生 WebSocket，房间状态放在**进程内存**里，不依赖任何数据库
- 玩法细节对齐 `docs/PRD-接词挑战.md`

> 房间是「短生命周期 + 强过期」的数据，又是小游戏，所以没有 Redis / 数据库：
> 房间 2 小时自动过期，**服务重启后房间会丢**，重新建一局就行。

---

## 目录结构

```
猜词demo/
├── backend/                     Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/wordmatch/
│       │   ├── controller/      REST：建房 / 房间预览 / 加入 / 健康检查
│       │   ├── ws/              WebSocket 连接管理 + 消息路由
│       │   ├── service/         回合状态机、判定、定时器、推送
│       │   ├── domain/          Room / Player / ChainItem
│       │   ├── store/           内存房间存储（带过期清理）
│       │   └── dto/             下发给客户端的快照投影
│       └── resources/
│           ├── application.yml
│           └── static/          前端构建产物（由 npm run build 同步进来）
├── frontend/                    Vue 3 前端
│   └── src/
│       ├── views/               首页 / 等待页 / 对局页 / 结算页
│       ├── game.js              WebSocket 封装（鉴权、心跳、重连、快照）
│       ├── api.js               建房 / 加入
│       ├── clipboard.js         复制（含局域网 http 兜底）
│       └── style.css
├── deploy/                     部署物料
│   ├── word-match.service      systemd 单元
│   ├── deploy.sh               一键打包 + 上传 + 重启
│   └── Dockerfile              不想在服务器装 JDK 时用
├── run.bat                      Windows 一键启动
├── build.sh                     一键构建（前端 + jar）
├── test/smoke.mjs               端到端冒烟测试（模拟两个玩家打完整一局）
└── docs/
    ├── PRD-接词挑战.md
    └── 部署-云服务器.md         公网 IP + 8080 的完整部署步骤
```

---

## 快速开始

### 环境

- JDK 17+、Maven 3.8+（只需构建时用）
- Node 18+（只有改前端才需要）

### 一步构建

```bash
bash build.sh
java -jar backend/target/word-match-0.1.0.jar --server.port=8080
```

`build.sh` 会：构建前端 → 同步到后端静态目录 → `mvn clean package`。
服务器上没装 Node 也能用——仓库里已经带了构建好的 `static` 产物，`build.sh` 检测不到 Node 时会跳过前端，直接用现成的。

Windows 上构建过一次之后，也可以直接双击 `run.bat` 启动。

手动等价命令：

```bash
cd frontend && npm install && npm run build
cd .. && rm -rf backend/src/main/resources/static && mkdir -p backend/src/main/resources/static && cp -r frontend/dist/. backend/src/main/resources/static/
cd backend && mvn -DskipTests clean package
```

打开 http://localhost:8080 ，建房间，把房间码/邀请链接发给朋友。
两个人用同一台电脑也行：一个正常窗口 + 一个无痕窗口。

> 想让手机连着玩：查本机内网 IP（`ipconfig`），手机访问 `http://192.168.x.x:8080` 即可。
> 注意这是 http 非安全上下文，页面里的「复制」已经做了 `execCommand` 兜底，不会点不动。

### 前后端分离开发

```bash
# 终端 A：后端
cd backend && mvn spring-boot:run

# 终端 B：前端（Vite 已配好 /api 与 /ws 代理到 8080）
cd frontend && npm install && npm run dev
```

访问 http://localhost:5173 。

### 单独调端口

`application.yml` 里写的是 `server.port: 8080`，命令行参数优先级更高：

```bash
java -jar target/word-match-0.1.0.jar --server.port=9000
```

---

## 部署到云服务器

整套东西是**一个 jar**：页面、`/api`、`/ws` 同进程，不需要 Nginx / 数据库，服务端只要有 JDK 17。

```bash
# 1. 本地打包
cd frontend && npm run build
cd .. && rm -rf backend/src/main/resources/static && cp -r frontend/dist backend/src/main/resources/static
cd backend && mvn -DskipTests clean package

# 2. 上传
scp target/word-match-0.1.0.jar root@<公网IP>:/opt/word-match/app.jar

# 3. 服务器上装 JDK 17 + systemd 常驻（详见文档），然后放行安全组 8080
```

之后每次发版：`bash deploy/deploy.sh root@<公网IP>`。

完整步骤、systemd 配置、安全组与防火墙、Docker 方式、域名 + HTTPS、排查手册见
**[docs/部署-云服务器.md](docs/部署-云服务器.md)**。

注意两点：

- **单实例运行**，别开多副本——房间状态在进程内存里
- 用 `http://IP:8080` 直连时是明文，复制按钮走的是 `execCommand` 兜底（能用，但绑域名 + HTTPS 体验更好）

---

## 配置项

`backend/src/main/resources/application.yml`，均可用 `--key=value` 覆盖：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `8080` | 服务端口 |
| `app.round-seconds` | `20` | 单回合输入时限（秒） |
| `app.room-ttl-seconds` | `7200` | 房间在内存里的存活时间，到期自动丢弃 |
| `app.reveal-millis` | `2400` | 揭晓停留时长，服务端控制推进节奏 |

---

## 规则实现要点

| 规则 | 实现 |
| --- | --- |
| 双盲 | 快照里**只有**「是否已提交」，本轮任何一方的词都在揭晓前不下发 |
| 判定 | 服务端归一化后比较：NFKC 全角转半角 → 去所有空白 → 小写 → 去首尾标点 |
| 不做的事 | 不判同义（`猫` vs `猫咪` 不算）、不纠错别字、不跨语言 |
| 提前结算 | 双方都提交立即判定，不等倒计时 |
| 超时 | 服务端定时器到点结算，未提交的一方记弃权（`null`），**双方弃权不判胜** |
| 禁复用 | 本局出现过的词（含锚点）不能再提交，堵死「第二轮抄对方」 |
| 锚点 | 第 1 轮用随机词/房主自定义词，之后是上一轮的两个词 |
| 评级 | 成功时按 `轮次 / 上限` 换算 S/A/B/C，自定义轮数不会失真 |
| 并发 | 按房间码 `ReentrantLock` 串行，双人同时提交只触发一次判定 |
| 过期 | 每个房间带过期时间，读时惰性删除，另有 10 分钟一次的扫描清理 |

### 协议

REST（进房间之前）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/rooms` | `{nickname, maxRounds, anchorMode, startAnchor}` → `{code, playerId, token, slot}` |
| `GET` | `/api/rooms/{code}` | 房间预览：是否存在、是否满员、房主昵称 |
| `POST` | `/api/rooms/{code}/join` | `{nickname}` → 同上 |
| `GET` | `/api/health` | 存活房间数、回合时长 |

WebSocket `ws://host/ws/game`，所有消息都是 `{ type, payload }`：

| 方向 | type | 说明 |
| --- | --- | --- |
| C→S | `auth` | 首帧鉴权 `{token}`，token 不放进 URL |
| S→C | `auth.ok` | `{playerId, slot, snapshot}` |
| S→C | `room.state` | 任意变更后推送全量快照（客户端只做投影） |
| C→S | `round.submit` | `{roundNo, word}`，`roundNo` 是回合栅栏 |
| C→S | `game.start` / `game.restart` / `game.giveup` | 房主开局 / 重开 / 放弃 |
| S→C | `error` | `{code, message}`，错误码同 REST |

---

## 测试

```bash
# 输入归一化 / 字数上限 / 光标位置（纯前端逻辑，不需要服务端）
node test/input.test.mjs

# 端到端对局（需要后端已启动）
node test/smoke.mjs
```

输入测试覆盖：中文 12 / 英文数字 20 / 混合 16 三档上限、切字符类型时新输入不被吞、
非法字符过滤、房间码归一化、以及「内容没变就不写 DOM」保证光标不被甩到末尾（26 项）。

对局测试覆盖：建房、第三人被拒、非房主开局被拒、历史词复用被拒、非法字符被拒、
对手词不泄露、揭晓、自动开下一轮、撞词判胜、评级、重开、20 秒超时结算（29 项）。

---

## 已知取舍 / 下一步

- **单实例**：房间锁是进程内的 `ReentrantLock`，多实例部署要换分布式锁
- **重启即丢房间**：内存存储的代价；要保住对局就加一层持久化（Redis / Redis Cluster）
- 分享二维码、`navigator.share`
- 断线 30 秒后的「继续等待 / 结束本局」弹窗（现在是不暂停，重连即恢复）
- 战绩与历史（需要加关系库）
- `token` 目前是 `房间码:玩家ID` 的可解析格式，够朋友局用；对外开放要做签名
