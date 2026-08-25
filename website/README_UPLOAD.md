# QCloudy_Addition 官网上传说明

这是一个纯静态网站，不需要 PHP、数据库或 Node.js。Nginx 可以直接提供网页。

## 上传到宝塔

1. 打开宝塔面板的“文件”。
2. 进入 `/www/wwwroot/64.90.0.16`。
3. 上传本次生成的 `QCloudy_Addition_Website-0.3.9-20260825.zip`。
4. 在当前目录解压压缩包。
5. 确认 `/www/wwwroot/64.90.0.16/index.html` 直接存在；不要多套一层 `website` 文件夹。
6. 在浏览器打开 `http://64.90.0.16/` 并强制刷新。

原来的 `index-nginx-backup.html` 可以保留。只要新的 `index.html` 位于站点根目录，Nginx 就会优先显示官网。

## 更新网站

以后更新时，重新上传并覆盖这些内容：

- `index.html`
- `download` 文件夹
- `features` 文件夹
- `compliance` 文件夹
- `changelog` 文件夹
- `assets` 文件夹
- `404.html`

不要删除 `.user.ini`、`.htaccess` 或宝塔生成的其他管理文件。

## 当前站点结构

```text
index.html
404.html
download/
  index.html
features/
  index.html
compliance/
  index.html
changelog/
  index.html
assets/
  css/style.css
  js/main.js
  js/download.js
  data/release-manifest.json
  images/
```

`/download/` 页面只展示最新的稳定 Release，并让实际 JAR 直接从 GitHub Releases 下载。Alpha、Beta、sources 与 javadocs 文件不会显示。发布新的 Release 时，同时更新 `assets/data/release-manifest.json`，这样即使 GitHub 接口暂时不可用，页面仍有经过验证的下载链接。

`/features/`、`/compliance/` 与 `/changelog/` 分别提供站内功能说明、纯客户端边界与当前稳定版更新日志。它们与主页使用同一套中英文切换、响应式导航和可重复滚动动画。

上传新压缩包时只覆盖同名网站文件与文件夹。保留服务器上的 `.well-known`、`.user.ini`、`.htaccess` 和宝塔生成的证书验证文件。

## 域名与 HTTPS

没有域名时可以先使用服务器 IP。购买域名后，在宝塔的网站设置中新增域名绑定，再为该域名申请 SSL 证书即可；网页文件本身不需要重做。
