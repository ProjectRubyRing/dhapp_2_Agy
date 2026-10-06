# ALB 経由リダイレクト Location ヘッダ HTTPS 修正確認 API

## 1. 概要と背景課題

本機能は、**ALB（HTTPS）〜 コンテナ/JBoss EAP（HTTP）間**の通信環境において、
JBoss EAP 内の Spring Boot アプリケーションがリダイレクト指示を出した際に、
**レスポンスの `Location` ヘッダが `https://...` に正しく修正されていることを確認・検証可能とする API 機能一式**です。

```
【通信経路】
[クライアント/ブラウザ]
       │  HTTPS (443)
       ▼
[ALB (または偽装 Nginx コンテナ)]
       │  HTTP (8080/8180)  ※TLS 終端
       │  + X-Forwarded-Proto: https
       │  + X-Forwarded-Port: 443
       │  + Host: <ALB ホスト名>
       ▼
[JBoss EAP / Undertow] (dhapp WAR)
       │
       ├─ [問題発生時] Undertow/アプリが平文 HTTP と誤認識 → Location: http://... (不具合)
       └─ [修正後]     X-Forwarded-* を解釈 → Location: https://... (正常)
```

### 1.1 判明した問題（根本原因）

1. **ALB での TLS 終端**: クライアントから ALB までは HTTPS（443）で暗号化されますが、ALB から JBoss EAP コンテナへはターゲットグループ経由で平文 HTTP（8080/8180）で転送されます。
2. **サーブレット仕様の相対リダイレクト補完**: アプリケーションが相対パス（例: `sendRedirect("/api/...")` や Spring MVC の `redirect:/...`）でリダイレクトを指示すると、サーブレットコンテナ（Undertow）は着信リクエストのスキーム・ホスト・ポートに基づいて絶対 URL を組み立てて `Location` ヘッダに設定します。
3. **未修正時の挙動**: Undertow または Spring が ALB の `X-Forwarded-Proto: https` を認識していない場合、内部ソケットの平文 HTTP スキームに基づき `Location: http://<ALB ホスト>/...` が生成されます。
4. **影響**: クライアントが HTTP へのリダイレクトを受け取ると、ブラウザでの Mixed Content 警告や、平文 HTTP ポート閉塞環境での接続タイムアウト・失敗が発生します。

### 1.2 修正のアプローチ（2 系統の防御）

本リポジトリでは、以下の **2 つのレイヤー** で本問題を解決可能にしています。

| レイヤー | 修正内容 | 説明 |
|---|---|---|
| **Spring アプリケーション層**（本アプリで同梱実装） | `ForwardedHeaderFilter` の登録（`RedirectConfig` / `server.forward-headers-strategy: framework`） | Spring のフィルターが `X-Forwarded-*` ヘッダを解析してリクエストとレスポンスをラップし、`sendRedirect()` 時に自動的に `https://` で始まる絶対 URL を生成する。Undertow 設定に手を入れられない場合でもアプリ単体で解決可能。 |
| **JBoss EAP / Undertow コンテナ層** | `proxy-address-forwarding="true"`（`wildfly/enable-proxy-address-forwarding.cli`） | JBoss EAP の Undertow サブシステム（`http-listener`）でプロキシヘッダの解釈を有効化し、コンテナ全体で `request.getScheme()` を `https` に補正する。 |

---

## 2. 提供エンドポイント一覧

コンテキストパスは `/iwinmichl` です。

| メソッド | パス | 役割 |
|---|---|---|
| `GET` / `POST` | `/api/redirect/trigger` | **リダイレクト指示発行 API**。JBoss EAP 内からリダイレクト（HTTP 302 等）を発行し、`Location` ヘッダを観察可能にする。 |
| `GET` / `POST` | `/api/redirect/target` | **リダイレクト着地確認 API**。リダイレクト後にクライアントが到達する着地先。到達時のスキームやヘッダを返す。 |
| `GET` / `POST` | `/api/redirect/check` | **リダイレクト Location ヘッダ検証 API**。ALB 偽装コンテナやローカルへリクエストを投げ、`Location` ヘッダが `https` になっているかを自動判定する。 |
| `GET` | `/api/redirect/info` | **現在のリクエスト環境認識確認 API**。コンテナが現在のリクエストをどう認識しているか（スキーム・ポート・生成予測 URL）を返す。 |

---

## 3. 各エンドポイントの詳細と使い方

### 3.1 `GET|POST /api/redirect/check` — リダイレクト検証 API

外部の ALB 偽装コンテナ（またはローカル）に HTTP/HTTPS リクエストを送信し、返却された HTTP 3xx 応答の `Location` ヘッダを捕捉して検証します（`followRedirects=false` で 302 を直接解析）。

#### クエリパラメータ

| パラメータ | 既定値 | 選択肢・説明 |
|---|---|---|
| `target` | `alb` | `alb` (偽装 ALB コンテナ経由) / `simulated` (X-Forwarded-* ヘッダ付与でローカル検証) / `plain` (ヘッダ無しの平文アクセス) / `comparison` (simulated と plain を両方実行して比較) / `custom` (url パラメータで指定) |
| `url` | (ターゲット既定) | 任意の接続先 URL（`target=custom` または上書き用） |
| `mode` | `sendRedirect` | `sendRedirect` (Servlet 標準) / `redirectView` (Spring MVC) / `responseEntity` (Spring ResponseEntity) |
| `insecure` | `true` | 自己署名証明書の検証をスキップするか（テスト環境用） |
| `format` | (なし) | `text` を指定するとコンソール出力と同じ人間用テキストレポートを返却 |

#### 動作確認コマンド例

```bash
# 1. 偽装 ALB コンテナ経由での検証（本命: ALB -> JBoss EAP の一連の通信を検証）
curl -s http://localhost:8080/iwinmichl/api/redirect/check | jq .

# 2. テキスト形式レポートで確認
curl -s "http://localhost:8080/iwinmichl/api/redirect/check?format=text"

# 3. 比較検証モード（X-Forwarded-Proto 付与時 vs 平文アクセス時の違いを一発証明）
curl -s "http://localhost:8080/iwinmichl/api/redirect/check?target=comparison" | jq .

# 4. ALB ヘッダを偽装してローカル直接検証（外部 ALB が無くてもアプリの修正効果を確認可能）
curl -s "http://localhost:8080/iwinmichl/api/redirect/check?target=simulated" | jq .

# 5. 平文アクセス（未修正時の挙動 http:// になることの対照実験）
curl -s "http://localhost:8080/iwinmichl/api/redirect/check?target=plain" | jq .
```

#### レスポンス例 (`GET /api/redirect/check`)

```json
{
  "requestId": "9d8e7f6a-1234-5678-abcd-ef0123456789",
  "timestamp": "2026-10-07T03:55:00.000+09:00",
  "target": "alb",
  "url": "https://alb/iwinmichl/api/redirect/trigger?mode=sendRedirect",
  "redirectMode": "sendRedirect",
  "status": "SUCCESS",
  "verdict": "LOCATION_HTTPS_OK",
  "summary": "【検証成功】Location ヘッダが正常に https (https://...) として設定されていることを確認しました。ALB (HTTPS) 〜 JBoss EAP (HTTP) 間におけるリダイレクト URL の HTTPS 化が正常に機能しています。",
  "isHttps": true,
  "httpStatus": 302,
  "locationHeader": "https://alb/iwinmichl/api/redirect/target",
  "locationScheme": "https",
  "locationHost": "alb",
  "locationPort": null,
  "locationPath": "/iwinmichl/api/redirect/target",
  "requestHeadersSent": {
    "Accept": "*/*",
    "User-Agent": "dhapp-redirect-verifier/1.0"
  },
  "responseHeaders": {
    "Location": "https://alb/iwinmichl/api/redirect/target",
    "X-Redirect-Request-Scheme": "https",
    "X-Redirect-Request-Secure": "true",
    "X-Redirect-Request-Port": "443"
  },
  "elapsedMs": 15,
  "troubleshooting": null,
  "report": "..."
}
```

---

### 3.2 `GET|POST /api/redirect/trigger` — リダイレクト指示発行 API

JBoss EAP 内のアプリがリダイレクト指示を出すエンドポイントです。外部から curl 等で直接叩いて `Location` ヘッダを確認する際にも使用できます。

#### クエリパラメータ

| パラメータ | 既定値 | 説明 |
|---|---|---|
| `target` | `/api/redirect/target` | リダイレクト先 URL または相対パス |
| `mode` | `sendRedirect` | `sendRedirect` / `redirectView` / `responseEntity` |
| `status` | `302` | HTTP ステータスコード（301, 302, 303, 307 等） |

#### 動作確認コマンド例

```bash
# 偽装 ALB 経由でリダイレクト指示を受け取り、Location ヘッダを直接確認
curl -i -k https://alb/iwinmichl/api/redirect/trigger

# JBoss EAP に直接 X-Forwarded-Proto: https を渡して確認
curl -i -H "X-Forwarded-Proto: https" \
        -H "X-Forwarded-Port: 443" \
        -H "Host: alb" \
        http://localhost:8080/iwinmichl/api/redirect/trigger
```

**期待されるレスポンスヘッダ:**
```http
HTTP/1.1 302 Found
Location: https://alb/iwinmichl/api/redirect/target
X-Redirect-Request-Scheme: https
X-Redirect-Request-Secure: true
X-Redirect-Request-Port: 443
X-Redirect-Mode: sendRedirect
```

---

### 3.3 `GET /api/redirect/info` — リクエスト認識確認 API

コンテナおよび Spring が、着信リクエストをどのように認識しているかを表示します。

```bash
curl -s http://localhost:8080/iwinmichl/api/redirect/info | jq .
```

#### レスポンス例

```json
{
  "requestId": "...",
  "timestamp": "2026-10-07T03:55:00.000+09:00",
  "requestScheme": "https",
  "isSecure": true,
  "serverName": "alb",
  "serverPort": 443,
  "contextPath": "/iwinmichl",
  "requestUri": "/iwinmichl/api/redirect/info",
  "forwardedHeaders": {
    "X-Forwarded-Proto": "https",
    "X-Forwarded-Port": "443",
    "X-Forwarded-Host": "alb",
    "Host": "alb"
  },
  "predictedSendRedirectUrl": "https://alb/iwinmichl/api/redirect/target",
  "forwardedHeaderFilterActive": true,
  "undertowProxyAddressForwardingHint": "Undertow 側での設定: /subsystem=undertow/server=default-server/http-listener=default:write-attribute(name=proxy-address-forwarding, value=true)"
}
```

---

## 4. JBoss EAP / Undertow 側での設定方法

アプリケーション側の `ForwardedHeaderFilter` に加え、JBoss EAP (Undertow) 側でも `proxy-address-forwarding="true"` を設定することが推奨されます。同梱の CLI スクリプトで簡単に適用できます。

```bash
# 起動中の JBoss EAP へ適用（自動リロード）
$JBOSS_HOME/bin/jboss-cli.sh --connect --file=wildfly/enable-proxy-address-forwarding.cli
```

`standalone.xml` を直接編集する場合:
```xml
<subsystem xmlns="urn:jboss:domain:undertow:14.0">
    <server name="default-server">
        <http-listener name="default" socket-binding="http" proxy-address-forwarding="true" redirect-socket="https"/>
        ...
    </server>
</subsystem>
```
