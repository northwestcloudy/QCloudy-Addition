(() => {
  "use strict";

  const API_URL = "https://api.github.com/repos/gprztb6nw4-dotcom/QCloudy-Addition/releases?per_page=100";
  const MANIFEST_URL = "../assets/data/release-manifest.json";
  const CACHE_KEY = "qca-stable-release-cache-v2";
  const CACHE_LIFETIME = 15 * 60 * 1000;
  const GITHUB_ASSET_PREFIX = "https://github.com/gprztb6nw4-dotcom/QCloudy-Addition/releases/download/";

  const assetList = document.querySelector("[data-release-assets]");
  const versionElement = document.querySelector("[data-release-version]");
  const dateElement = document.querySelector("[data-release-date]");
  const channelElement = document.querySelector("[data-release-channel]");
  const statusElement = document.querySelector("[data-release-status]");
  const notesLink = document.querySelector("[data-release-notes]");

  if (!assetList || !versionElement || !dateElement || !notesLink) return;

  let currentRelease = null;
  let currentSource = "fallback";

  function language() {
    return localStorage.getItem("qca-site-language") === "zh" ? "zh" : "en";
  }

  function localized(en, zh) {
    return language() === "zh" ? zh : en;
  }

  function isPlayableReleaseAsset(asset) {
    if (!asset || typeof asset.name !== "string" || typeof asset.browser_download_url !== "string") return false;
    const name = asset.name.toLowerCase();
    return name.startsWith("qcloudy_addition-")
      && name.endsWith(".jar")
      && name.includes("release")
      && !name.includes("sources")
      && !name.includes("javadoc")
      && asset.browser_download_url.startsWith(GITHUB_ASSET_PREFIX);
  }

  function isStableRelease(release) {
    if (!release || release.draft || release.prerelease) return false;
    const identity = `${release.tag_name || ""} ${release.name || ""}`.toLowerCase();
    if (/\b(alpha|beta|snapshot|pre[- ]?release)\b/.test(identity)) return false;
    const playableAssets = Array.isArray(release.assets)
      ? release.assets.filter(isPlayableReleaseAsset)
      : [];
    return playableAssets.length > 0
      && (identity.includes("release") || playableAssets.every((asset) => asset.name.toLowerCase().includes("release")));
  }

  function extractMinecraftVersion(name) {
    const match = name.match(/\+([0-9]+(?:\.[0-9]+)+)(?:-Release(?:-[0-9]+)?|\.jar)/i);
    return match ? match[1] : null;
  }

  function extractModVersion(release, assets) {
    const identity = `${release.name || ""} ${release.tag_name || ""}`;
    const named = identity.match(/(?:Release\s+|\bv)([0-9]+(?:\.[0-9]+)+)/i);
    if (named) return named[1];

    for (const asset of assets) {
      const current = asset.name.match(/^QCloudy_Addition-(.+?)\+/i);
      if (current) return current[1].replace(/^Release-/i, "").replace(/-Release(?:-[0-9]+)?$/i, "");
    }
    return release.tag_name || "Release";
  }

  function compareVersions(left, right) {
    const a = left.split(".").map(Number);
    const b = right.split(".").map(Number);
    const length = Math.max(a.length, b.length);
    for (let index = 0; index < length; index += 1) {
      const difference = (a[index] || 0) - (b[index] || 0);
      if (difference !== 0) return difference;
    }
    return 0;
  }

  function normalizeLiveRelease(release) {
    const uniqueByMinecraft = new Map();
    release.assets.filter(isPlayableReleaseAsset).forEach((asset) => {
      const minecraft = extractMinecraftVersion(asset.name);
      if (!minecraft || uniqueByMinecraft.has(minecraft)) return;
      uniqueByMinecraft.set(minecraft, {
        minecraft,
        name: asset.name,
        size: Number(asset.size) || 0,
        digest: typeof asset.digest === "string" ? asset.digest : "",
        downloadCount: Number(asset.download_count) || 0,
        url: asset.browser_download_url
      });
    });

    const assets = [...uniqueByMinecraft.values()].sort((a, b) => compareVersions(a.minecraft, b.minecraft));
    if (!assets.length) return null;

    return {
      channel: "Release",
      version: extractModVersion(release, assets),
      tag: release.tag_name || "",
      publishedAt: release.published_at || release.created_at || "",
      releaseUrl: release.html_url || "https://github.com/gprztb6nw4-dotcom/QCloudy-Addition/releases",
      assets
    };
  }

  function newestStableRelease(releases) {
    if (!Array.isArray(releases)) return null;
    const stable = releases
      .filter(isStableRelease)
      .sort((a, b) => new Date(b.published_at || b.created_at || 0) - new Date(a.published_at || a.created_at || 0));
    return stable.length ? normalizeLiveRelease(stable[0]) : null;
  }

  function validNormalizedRelease(release) {
    return Boolean(
      release
      && release.channel === "Release"
      && typeof release.version === "string"
      && typeof release.releaseUrl === "string"
      && Array.isArray(release.assets)
      && release.assets.length
      && release.assets.every((asset) => (
        typeof asset.minecraft === "string"
        && typeof asset.name === "string"
        && typeof asset.url === "string"
        && asset.url.startsWith(GITHUB_ASSET_PREFIX)
        && asset.name.toLowerCase().endsWith(".jar")
        && !asset.name.toLowerCase().includes("sources")
      ))
    );
  }

  function formatSize(bytes) {
    if (!Number.isFinite(bytes) || bytes <= 0) return localized("JAR file", "JAR 文件");
    if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
    return `${Math.round(bytes / 1024)} KB`;
  }

  function formatDate(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return localized("Date unavailable", "日期未知");
    return new Intl.DateTimeFormat(language() === "zh" ? "zh-CN" : "en-GB", {
      year: "numeric",
      month: language() === "zh" ? "long" : "short",
      day: "numeric"
    }).format(date);
  }

  function createText(tagName, className, text) {
    const element = document.createElement(tagName);
    if (className) element.className = className;
    element.textContent = text;
    return element;
  }

  function createAssetCard(asset) {
    const article = document.createElement("article");
    article.className = "release-file-card";

    const icon = document.createElement("div");
    icon.className = "release-file-icon";
    icon.setAttribute("aria-hidden", "true");
    const iconImage = document.createElement("img");
    iconImage.src = "../assets/images/qcloudy-icon.png";
    iconImage.alt = "";
    iconImage.width = 54;
    iconImage.height = 54;
    icon.append(iconImage);

    const copy = document.createElement("div");
    copy.className = "release-file-copy";
    copy.append(
      createText("span", "release-file-kicker", "MINECRAFT"),
      createText("h3", "", asset.minecraft),
      createText("p", "", asset.name)
    );

    const meta = document.createElement("div");
    meta.className = "release-file-meta";
    meta.append(
      createText("span", "", formatSize(Number(asset.size))),
      createText("span", "", "Java 25"),
      createText("span", "", "Fabric")
    );
    copy.append(meta);

    if (asset.digest) {
      const digest = createText("code", "release-file-digest", asset.digest.replace(/^sha256:/i, "SHA-256 "));
      digest.title = asset.digest;
      copy.append(digest);
    }

    const button = document.createElement("a");
    button.className = "button button-download";
    button.href = asset.url;
    button.rel = "noreferrer";
    button.append(
      createText("span", "", localized("Download JAR", "下载 JAR")),
      createText("span", "", "↓")
    );

    article.append(icon, copy, button);
    return article;
  }

  function renderRelease(release, source) {
    if (!validNormalizedRelease(release)) return;
    currentRelease = release;
    currentSource = source;

    versionElement.textContent = release.version;
    channelElement.textContent = release.channel;
    dateElement.textContent = formatDate(release.publishedAt);
    dateElement.dateTime = release.publishedAt || "";
    notesLink.href = "../changelog/";

    statusElement.textContent = source === "github"
      ? localized("Live GitHub data", "GitHub 实时数据")
      : localized("Verified fallback", "已验证后备数据");
    statusElement.classList.toggle("is-live", source === "github");

    const fragment = document.createDocumentFragment();
    release.assets.forEach((asset) => fragment.append(createAssetCard(asset)));
    assetList.replaceChildren(fragment);
  }

  function readCache() {
    try {
      const cached = JSON.parse(localStorage.getItem(CACHE_KEY) || "null");
      if (!cached || Date.now() - cached.savedAt > CACHE_LIFETIME || !validNormalizedRelease(cached.release)) return null;
      return cached.release;
    } catch (_error) {
      return null;
    }
  }

  function writeCache(release) {
    try {
      localStorage.setItem(CACHE_KEY, JSON.stringify({ savedAt: Date.now(), release }));
    } catch (_error) {
      // The page remains fully usable when private browsing blocks storage.
    }
  }

  async function loadManifest() {
    const response = await fetch(MANIFEST_URL, { cache: "no-cache" });
    if (!response.ok) throw new Error(`Manifest request failed: ${response.status}`);
    const release = await response.json();
    if (!validNormalizedRelease(release)) throw new Error("Manifest data is invalid");
    return release;
  }

  async function loadGitHubRelease() {
    const response = await fetch(API_URL, {
      headers: { Accept: "application/vnd.github+json" }
    });
    if (!response.ok) throw new Error(`GitHub request failed: ${response.status}`);
    const release = newestStableRelease(await response.json());
    if (!release) throw new Error("No stable Release assets were found");
    return release;
  }

  document.querySelector("[data-language-toggle]")?.addEventListener("click", () => {
    if (currentRelease) renderRelease(currentRelease, currentSource);
  });

  const cached = readCache();
  if (cached) renderRelease(cached, "github");

  Promise.allSettled([loadManifest(), loadGitHubRelease()]).then(([manifestResult, githubResult]) => {
    if (githubResult.status === "fulfilled") {
      writeCache(githubResult.value);
      renderRelease(githubResult.value, "github");
      return;
    }
    if (!cached && manifestResult.status === "fulfilled") {
      renderRelease(manifestResult.value, "fallback");
      return;
    }
    if (!cached) {
      statusElement.textContent = localized("Static verified links", "静态已验证链接");
    }
  });
})();
