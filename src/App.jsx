import { useEffect, useMemo, useState } from "react";
import { api } from "./api.js";

const copy = {
  uz: {
    brand: "SAMP MARKET",
    tagline: "SAMP, launcher va APK fayllar uchun yagona platforma",
    heroTitle: "Play Market uslubidagi SAMP ekotizimi",
    heroBody:
      "Yangiliklar, kategoriyalar, reyting, izohlar va fayl yuklash bir joyda. Shu backendga Android ilovani ham ulaymiz.",
    heroPrimary: "Platformani sinash",
    heroSecondary: "Fayl yuklash",
    latest: "Oxirgi publikatsiyalar",
    top: "Top yuklanmalar",
    categories: "Kategoriyalar",
    news: "Yangiliklar",
    authTitle: "Akkauntga kirish",
    registerTitle: "Ro'yxatdan o'tish",
    email: "Email",
    password: "Parol",
    nickname: "Nik",
    login: "Kirish",
    register: "Ro'yxatdan o'tish",
    logout: "Chiqish",
    profile: "Profil",
    upload: "Fayl yuklash",
    uploadNow: "Publikatsiya qilish",
    search: "Qidiruv",
    description: "Tavsif",
    category: "Kategoriya",
    version: "Versiya",
    size: "Hajmi",
    directLink: "Tashqi yuklash linki",
    chooseFile: "Asosiy fayl",
    chooseCover: "Muqova rasmi",
    language: "Til",
    comments: "Izohlar",
    rating: "Baho",
    save: "Saqlash",
    welcome: "Xush kelibsiz",
    noFiles: "Hali fayllar yo'q",
    all: "Barchasi",
    downloads: "yuklanma",
    publishHint: "APK, zip yoki boshqa fayllarni yuklang. Render diskga saqlanadi.",
    profileHint: "Android ilovadagi server URL shu API manziliga o'rnatiladi.",
    open: "Ochish",
    send: "Yuborish",
    successUpload: "Fayl muvaffaqiyatli yuklandi",
    uiTitle: "Maqsadli dizayn",
    uiBody: "Kartalar, discovery section, editorial banner va sticky header bilan zamonaviy ko'rinish."
  },
  ru: {
    brand: "SAMP MARKET",
    tagline: "Единая платформа для SAMP, launcher и APK файлов",
    heroTitle: "SAMP-экосистема в стиле Play Market",
    heroBody:
      "Новости, категории, рейтинги, комментарии и загрузка файлов в одном месте. К этому backend позже подключим Android-приложение.",
    heroPrimary: "Попробовать платформу",
    heroSecondary: "Загрузить файл",
    latest: "Последние публикации",
    top: "Популярные загрузки",
    categories: "Категории",
    news: "Новости",
    authTitle: "Вход в аккаунт",
    registerTitle: "Регистрация",
    email: "Email",
    password: "Пароль",
    nickname: "Ник",
    login: "Войти",
    register: "Регистрация",
    logout: "Выйти",
    profile: "Профиль",
    upload: "Загрузка файла",
    uploadNow: "Опубликовать",
    search: "Поиск",
    description: "Описание",
    category: "Категория",
    version: "Версия",
    size: "Размер",
    directLink: "Внешняя ссылка на загрузку",
    chooseFile: "Основной файл",
    chooseCover: "Обложка",
    language: "Язык",
    comments: "Комментарии",
    rating: "Оценка",
    save: "Сохранить",
    welcome: "Добро пожаловать",
    noFiles: "Пока нет файлов",
    all: "Все",
    downloads: "загрузок",
    publishHint: "Загружайте APK, zip или другие файлы. На Render они будут храниться на диске.",
    profileHint: "URL сервера в Android-приложении позже будет указывать на этот API.",
    open: "Открыть",
    send: "Отправить",
    successUpload: "Файл успешно загружен",
    uiTitle: "Продуманный дизайн",
    uiBody: "Карточки, discovery-секция, editorial banner и sticky header для современного вида."
  }
};

const tokenKey = "samp-market-token";
const localeKey = "samp-market-locale";

export default function App() {
  const [locale, setLocale] = useState(localStorage.getItem(localeKey) || "uz");
  const [home, setHome] = useState(null);
  const [files, setFiles] = useState([]);
  const [selectedFile, setSelectedFile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [token, setToken] = useState(localStorage.getItem(tokenKey) || "");
  const [profile, setProfile] = useState(null);
  const [authMode, setAuthMode] = useState("login");
  const [authForm, setAuthForm] = useState({ email: "", password: "", nickname: "" });
  const [profileForm, setProfileForm] = useState({ nickname: "", avatarUrl: "" });
  const [comment, setComment] = useState("");
  const [uploadState, setUploadState] = useState({
    title: "",
    description: "",
    category: "apk",
    version: "1.0.0",
    size: "",
    downloadUrl: "",
    language: "all",
    file: null,
    cover: null
  });
  const [message, setMessage] = useState("");
  const t = copy[locale];

  useEffect(() => {
    localStorage.setItem(localeKey, locale);
  }, [locale]);

  useEffect(() => {
    Promise.all([api.home(), api.files()])
      .then(([homeData, fileData]) => {
        setHome(homeData);
        setFiles(fileData);
        setSelectedFile(fileData[0] || null);
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!token) {
      setProfile(null);
      return;
    }
    api.profile(token)
      .then((data) => {
        setProfile(data);
        setProfileForm({
          nickname: data.nickname || "",
          avatarUrl: data.avatarUrl || ""
        });
      })
      .catch(() => {
        setToken("");
        localStorage.removeItem(tokenKey);
      });
  }, [token]);

  const filteredFiles = useMemo(() => {
    const q = search.toLowerCase();
    return files.filter((file) => !q || `${file.title} ${file.description}`.toLowerCase().includes(q));
  }, [files, search]);

  async function handleAuthSubmit(event) {
    event.preventDefault();
    setMessage("");
    const action = authMode === "login" ? api.login : api.register;
    try {
      const result = await action(authForm);
      localStorage.setItem(tokenKey, result.token);
      setToken(result.token);
      setAuthForm({ email: "", password: "", nickname: "" });
    } catch (error) {
      setMessage(error.message);
    }
  }

  async function refreshFiles(selectedId) {
    const next = await api.files();
    setFiles(next);
    const target = next.find((item) => item.id === selectedId) || next[0] || null;
    setSelectedFile(target);
    const homeData = await api.home();
    setHome(homeData);
  }

  async function handleUpload(event) {
    event.preventDefault();
    if (!token) {
      setMessage("Login required");
      return;
    }

    const formData = new FormData();
    Object.entries(uploadState).forEach(([key, value]) => {
      if (value) formData.append(key, value);
    });

    try {
      const created = await api.uploadFile(token, formData);
      setMessage(t.successUpload);
      setUploadState({
        title: "",
        description: "",
        category: "apk",
        version: "1.0.0",
        size: "",
        downloadUrl: "",
        language: "all",
        file: null,
        cover: null
      });
      await refreshFiles(created.id);
    } catch (error) {
      setMessage(error.message);
    }
  }

  async function handleProfileSave(event) {
    event.preventDefault();
    try {
      const updated = await api.updateProfile(token, profileForm);
      setProfile((current) => ({ ...current, ...updated }));
      await refreshFiles(selectedFile?.id);
      setMessage("Saved");
    } catch (error) {
      setMessage(error.message);
    }
  }

  async function handleCommentSubmit(event) {
    event.preventDefault();
    if (!selectedFile || !comment.trim()) return;
    try {
      await api.addComment(token, selectedFile.id, comment.trim());
      setComment("");
      await refreshFiles(selectedFile.id);
    } catch (error) {
      setMessage(error.message);
    }
  }

  async function handleRate(value) {
    if (!selectedFile) return;
    try {
      await api.addRating(token, selectedFile.id, value);
      await refreshFiles(selectedFile.id);
    } catch (error) {
      setMessage(error.message);
    }
  }

  async function openDownload(file) {
    await api.addDownload(file.id);
    if (file.fileUrl) {
      window.open(file.fileUrl, "_blank");
    } else if (file.downloadUrl) {
      window.open(file.downloadUrl, "_blank");
    }
    refreshFiles(file.id);
  }

  return (
    <div className="page-shell">
      <header className="topbar">
        <div>
          <div className="brand">{t.brand}</div>
          <div className="tagline">{t.tagline}</div>
        </div>
        <div className="topbar-actions">
          <button className="chip" onClick={() => setLocale(locale === "uz" ? "ru" : "uz")}>
            {locale.toUpperCase()}
          </button>
          {profile ? (
            <button
              className="chip chip-muted"
              onClick={() => {
                setToken("");
                localStorage.removeItem(tokenKey);
              }}
            >
              {t.logout}
            </button>
          ) : null}
        </div>
      </header>

      <main className="layout">
        <section className="hero-card">
          <div className="hero-copy">
            <p className="eyebrow">SAMP Ecosystem</p>
            <h1>{t.heroTitle}</h1>
            <p>{t.heroBody}</p>
            <div className="hero-actions">
              <a href="#catalog" className="button-primary">
                {t.heroPrimary}
              </a>
              <a href="#upload" className="button-secondary">
                {t.heroSecondary}
              </a>
            </div>
          </div>
          <div className="hero-panel">
            <div className="stat-card">
              <span>API</span>
              <strong>/api/files</strong>
            </div>
            <div className="stat-card">
              <span>{t.categories}</span>
              <strong>{home?.categories?.length || 0}</strong>
            </div>
            <div className="stat-card">
              <span>{t.latest}</span>
              <strong>{home?.stats?.totalFiles || 0}</strong>
            </div>
          </div>
        </section>

        <section className="discover-grid">
          <article className="editorial-card">
            <h3>{t.uiTitle}</h3>
            <p>{t.uiBody}</p>
          </article>
          <article className="editorial-card accent">
            <h3>Render Ready</h3>
            <p>{t.publishHint}</p>
          </article>
          <article className="editorial-card cool">
            <h3>Android API</h3>
            <p>{t.profileHint}</p>
          </article>
        </section>

        <section className="content-grid">
          <div className="main-column">
            <div className="section-heading" id="catalog">
              <h2>{t.categories}</h2>
              <div className="category-row">
                <button className="chip chip-solid" onClick={() => setSearch("")}>
                  {t.all}
                </button>
                {home?.categories?.map((category) => (
                  <button key={category.id} className="chip" onClick={() => setSearch(category.name)}>
                    {category.name}
                  </button>
                ))}
              </div>
            </div>

            <div className="search-box">
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder={t.search}
              />
            </div>

            <div className="cards-grid">
              {(filteredFiles.length ? filteredFiles : []).map((file) => (
                <article
                  key={file.id}
                  className={`file-card ${selectedFile?.id === file.id ? "active" : ""}`}
                  onClick={() => setSelectedFile(file)}
                >
                  <div className="cover">
                    {file.coverUrl ? <img src={file.coverUrl} alt={file.title} /> : <span>{file.title[0]}</span>}
                  </div>
                  <div className="file-meta">
                    <div className="file-category">{file.category}</div>
                    <h3>{file.title}</h3>
                    <p>{file.description}</p>
                    <div className="file-stats">
                      <span>{file.rating || 0}★</span>
                      <span>{file.downloads} {t.downloads}</span>
                    </div>
                  </div>
                </article>
              ))}
              {!filteredFiles.length && <div className="empty-card">{t.noFiles}</div>}
            </div>
          </div>

          <aside className="side-column">
            <section className="panel">
              <div className="section-heading">
                <h2>{t.top}</h2>
              </div>
              <div className="mini-list">
                {home?.topFiles?.map((file) => (
                  <button key={file.id} className="mini-item" onClick={() => setSelectedFile(file)}>
                    <span>{file.title}</span>
                    <strong>{file.downloads}</strong>
                  </button>
                ))}
              </div>
            </section>

            <section className="panel">
              <div className="section-heading">
                <h2>{t.news}</h2>
              </div>
              <div className="news-list">
                {home?.news?.map((item) => (
                  <article key={item.id} className="news-item">
                    <h4>{item.title[locale]}</h4>
                    <p>{item.body[locale]}</p>
                  </article>
                ))}
              </div>
            </section>
          </aside>
        </section>

        <section className="details-grid">
          <article className="panel detail-panel">
            <div className="section-heading">
              <h2>{selectedFile?.title || t.latest}</h2>
            </div>
            {selectedFile ? (
              <>
                <p className="detail-description">{selectedFile.description}</p>
                <div className="detail-meta">
                  <span>{t.category}: {selectedFile.category}</span>
                  <span>{t.version}: {selectedFile.version}</span>
                  <span>{t.size}: {selectedFile.size}</span>
                  <span>{t.rating}: {selectedFile.rating || 0} / 5</span>
                </div>
                <div className="rate-row">
                  {[1, 2, 3, 4, 5].map((value) => (
                    <button key={value} className="rate-button" onClick={() => handleRate(value)}>
                      {value}★
                    </button>
                  ))}
                </div>
                <div className="hero-actions">
                  <button className="button-primary" onClick={() => openDownload(selectedFile)}>
                    {t.open}
                  </button>
                </div>
                <div className="comment-section">
                  <h3>{t.comments}</h3>
                  <form onSubmit={handleCommentSubmit} className="comment-form">
                    <textarea value={comment} onChange={(event) => setComment(event.target.value)} />
                    <button className="button-secondary" type="submit">
                      {t.send}
                    </button>
                  </form>
                  <div className="comment-list">
                    {(selectedFile.comments || []).map((item) => (
                      <div key={item.id} className="comment-item">
                        <strong>{item.authorName}</strong>
                        <p>{item.message}</p>
                      </div>
                    ))}
                  </div>
                </div>
              </>
            ) : null}
          </article>

          <div className="stacked-column">
            <section className="panel auth-panel">
              <div className="tab-row">
                <button className={authMode === "login" ? "tab active" : "tab"} onClick={() => setAuthMode("login")}>
                  {t.login}
                </button>
                <button className={authMode === "register" ? "tab active" : "tab"} onClick={() => setAuthMode("register")}>
                  {t.register}
                </button>
              </div>
              {profile ? (
                <form onSubmit={handleProfileSave} className="form-grid">
                  <h3>{t.profile}</h3>
                  <input
                    value={profileForm.nickname}
                    onChange={(event) => setProfileForm((current) => ({ ...current, nickname: event.target.value }))}
                    placeholder={t.nickname}
                  />
                  <input
                    value={profileForm.avatarUrl}
                    onChange={(event) => setProfileForm((current) => ({ ...current, avatarUrl: event.target.value }))}
                    placeholder="Avatar URL"
                  />
                  <button className="button-primary" type="submit">
                    {t.save}
                  </button>
                </form>
              ) : (
                <form onSubmit={handleAuthSubmit} className="form-grid">
                  <h3>{authMode === "login" ? t.authTitle : t.registerTitle}</h3>
                  {authMode === "register" ? (
                    <input
                      value={authForm.nickname}
                      onChange={(event) => setAuthForm((current) => ({ ...current, nickname: event.target.value }))}
                      placeholder={t.nickname}
                    />
                  ) : null}
                  <input
                    type="email"
                    value={authForm.email}
                    onChange={(event) => setAuthForm((current) => ({ ...current, email: event.target.value }))}
                    placeholder={t.email}
                  />
                  <input
                    type="password"
                    value={authForm.password}
                    onChange={(event) => setAuthForm((current) => ({ ...current, password: event.target.value }))}
                    placeholder={t.password}
                  />
                  <button className="button-primary" type="submit">
                    {authMode === "login" ? t.login : t.register}
                  </button>
                </form>
              )}
            </section>

            <section className="panel upload-panel" id="upload">
              <form onSubmit={handleUpload} className="form-grid">
                <h3>{t.upload}</h3>
                <input
                  value={uploadState.title}
                  onChange={(event) => setUploadState((current) => ({ ...current, title: event.target.value }))}
                  placeholder="Title"
                />
                <textarea
                  value={uploadState.description}
                  onChange={(event) => setUploadState((current) => ({ ...current, description: event.target.value }))}
                  placeholder={t.description}
                />
                <select
                  value={uploadState.category}
                  onChange={(event) => setUploadState((current) => ({ ...current, category: event.target.value }))}
                >
                  {home?.categories?.map((category) => (
                    <option key={category.id} value={category.slug}>
                      {category.name}
                    </option>
                  ))}
                </select>
                <input
                  value={uploadState.version}
                  onChange={(event) => setUploadState((current) => ({ ...current, version: event.target.value }))}
                  placeholder={t.version}
                />
                <input
                  value={uploadState.size}
                  onChange={(event) => setUploadState((current) => ({ ...current, size: event.target.value }))}
                  placeholder={t.size}
                />
                <input
                  value={uploadState.downloadUrl}
                  onChange={(event) => setUploadState((current) => ({ ...current, downloadUrl: event.target.value }))}
                  placeholder={t.directLink}
                />
                <label className="file-label">
                  <span>{t.chooseFile}</span>
                  <input type="file" onChange={(event) => setUploadState((current) => ({ ...current, file: event.target.files[0] }))} />
                </label>
                <label className="file-label">
                  <span>{t.chooseCover}</span>
                  <input type="file" accept="image/*" onChange={(event) => setUploadState((current) => ({ ...current, cover: event.target.files[0] }))} />
                </label>
                <button className="button-primary" type="submit">
                  {t.uploadNow}
                </button>
              </form>
            </section>
          </div>
        </section>

        {message ? <div className="toast">{message}</div> : null}
      </main>
    </div>
  );
}
