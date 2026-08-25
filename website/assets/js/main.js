(() => {
  "use strict";

  const root = document.documentElement;
  const header = document.querySelector("[data-header]");
  const nav = document.querySelector("[data-nav]");
  const menuButton = document.querySelector("[data-menu-button]");
  const languageButton = document.querySelector("[data-language-toggle]");
  const languageLabel = document.querySelector("[data-language-label]");
  const translatedElements = [...document.querySelectorAll("[data-en][data-zh]")];

  const savedLanguage = localStorage.getItem("qca-site-language");
  let language = savedLanguage === "zh" ? "zh" : "en";

  function setLanguage(nextLanguage) {
    language = nextLanguage === "zh" ? "zh" : "en";
    root.lang = language === "zh" ? "zh-CN" : "en";

    translatedElements.forEach((element) => {
      element.textContent = element.dataset[language];
    });

    if (languageLabel) {
      languageLabel.textContent = language === "en" ? "中文" : "EN";
    }

    if (languageButton) {
      languageButton.setAttribute(
        "aria-label",
        language === "en" ? "Switch to Simplified Chinese" : "切换到英文"
      );
    }

    localStorage.setItem("qca-site-language", language);
  }

  function closeMenu() {
    document.body.classList.remove("nav-open");
    if (menuButton) menuButton.setAttribute("aria-expanded", "false");
  }

  languageButton?.addEventListener("click", () => {
    setLanguage(language === "en" ? "zh" : "en");
  });

  menuButton?.addEventListener("click", () => {
    const opening = !document.body.classList.contains("nav-open");
    document.body.classList.toggle("nav-open", opening);
    menuButton.setAttribute("aria-expanded", String(opening));
  });

  nav?.querySelectorAll("a").forEach((link) => {
    link.addEventListener("click", closeMenu);
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") closeMenu();
  });

  function updateHeader() {
    header?.classList.toggle("scrolled", window.scrollY > 24);
  }

  updateHeader();
  window.addEventListener("scroll", updateHeader, { passive: true });

  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const revealElements = [...document.querySelectorAll(".reveal")];
  const revealThreshold = 0.12;
  if (reducedMotion) {
    revealElements.forEach((element) => element.classList.add("visible"));
  } else if ("IntersectionObserver" in window) {
    const revealObserver = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          const visible = entry.isIntersecting && entry.intersectionRatio >= revealThreshold;
          entry.target.classList.toggle("visible", visible);
        });
      },
      { threshold: [0, revealThreshold], rootMargin: "0px 0px -6%" }
    );
    revealElements.forEach((element) => revealObserver.observe(element));
  } else {
    revealElements.forEach((element) => element.classList.add("visible"));
  }

  const sectionLinks = [...document.querySelectorAll(".site-nav a[href^='#']")];
  const sections = sectionLinks
    .map((link) => document.querySelector(link.getAttribute("href")))
    .filter(Boolean);

  if (sections.length) {
    let sectionFrame = 0;

    const updateActiveSection = () => {
      sectionFrame = 0;
      const headerHeight = header?.getBoundingClientRect().height ?? 0;
      const markerY = headerHeight + 12;
      let activeSection = null;

      sections.forEach((section) => {
        if (section.getBoundingClientRect().top <= markerY) activeSection = section;
      });

      if (window.location.hash) {
        const hashSection = sections.find((section) => `#${section.id}` === window.location.hash);
        const hashBounds = hashSection?.getBoundingClientRect();
        if (hashBounds && hashBounds.top <= markerY + 56 && hashBounds.bottom > markerY) {
          activeSection = hashSection;
        }
      }

      sectionLinks.forEach((link) => {
        link.classList.toggle(
          "active",
          Boolean(activeSection) && link.getAttribute("href") === `#${activeSection.id}`
        );
      });
    };

    const queueActiveSectionUpdate = () => {
      if (!sectionFrame) sectionFrame = window.requestAnimationFrame(updateActiveSection);
    };

    updateActiveSection();
    sectionLinks.forEach((link) => {
      link.addEventListener("click", () => {
        sectionLinks.forEach((item) => item.classList.toggle("active", item === link));
        window.requestAnimationFrame(() => window.requestAnimationFrame(updateActiveSection));
      });
    });
    window.addEventListener("scroll", queueActiveSectionUpdate, { passive: true });
    window.addEventListener("resize", queueActiveSectionUpdate);
    window.addEventListener("hashchange", () => {
      updateActiveSection();
      window.requestAnimationFrame(() => window.requestAnimationFrame(updateActiveSection));
    });
    window.addEventListener("load", updateActiveSection);
    document.fonts?.ready.then(updateActiveSection);
    if ("ResizeObserver" in window) {
      const sectionResizeObserver = new ResizeObserver(queueActiveSectionUpdate);
      sections.forEach((section) => sectionResizeObserver.observe(section));
    }
  }

  const map = document.querySelector(".map-frame");
  const marker = document.querySelector(".map-marker");
  const finePointer = window.matchMedia("(pointer: fine)").matches;

  if (map && marker && finePointer && !reducedMotion) {
    map.addEventListener("pointermove", (event) => {
      const bounds = map.getBoundingClientRect();
      const x = Math.min(76, Math.max(24, ((event.clientX - bounds.left) / bounds.width) * 100));
      const y = Math.min(75, Math.max(25, ((event.clientY - bounds.top) / bounds.height) * 100));
      marker.style.setProperty("--marker-x", `${x}%`);
      marker.style.setProperty("--marker-y", `${y}%`);
    });
    map.addEventListener("pointerleave", () => {
      marker.style.removeProperty("--marker-x");
      marker.style.removeProperty("--marker-y");
    });
  }

  const faqItems = [...document.querySelectorAll(".faq-list details")];

  if (!reducedMotion) {
    faqItems.forEach((details) => {
      const summary = details.querySelector("summary");
      if (!summary) return;

      let desiredOpen = details.open;
      let animation = null;

      const finish = (open, runningAnimation) => {
        if (animation !== runningAnimation) return;
        details.open = open;
        details.classList.remove("is-opening", "is-closing");
        details.style.removeProperty("height");
        details.style.removeProperty("overflow");
        animation = null;
      };

      const animate = (open) => {
        const currentHeight = details.getBoundingClientRect().height;

        if (animation) animation.cancel();

        details.open = true;
        details.classList.toggle("is-opening", open);
        details.classList.toggle("is-closing", !open);
        details.style.height = `${currentHeight}px`;
        details.style.overflow = "hidden";

        const borderHeight = details.offsetHeight - details.clientHeight;
        const targetHeight = open
          ? details.scrollHeight + borderHeight
          : summary.offsetHeight + borderHeight;

        const runningAnimation = details.animate(
          [
            { height: `${currentHeight}px` },
            { height: `${targetHeight}px` }
          ],
          {
            duration: open ? 380 : 320,
            easing: "cubic-bezier(.2, .75, .2, 1)"
          }
        );

        animation = runningAnimation;
        runningAnimation.onfinish = () => finish(open, runningAnimation);
        runningAnimation.oncancel = () => {
          if (animation === runningAnimation) animation = null;
        };
      };

      summary.addEventListener("click", (event) => {
        event.preventDefault();
        desiredOpen = !desiredOpen;
        animate(desiredOpen);
      });
    });
  }

  setLanguage(language);
})();
