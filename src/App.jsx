import { useEffect, useMemo, useState } from "react";
import { api } from "./api.js";

const copy = {
  uz: {
    brand: "SAMP STORE",
    owner: "SAMP STUDIO & AZIZ",
    tagline: "SAMP, launcher, APK va mod fayllari uchun yagona platforma",
    heroTitle: "Telefon va sayt uchun bitta backend, bitta baza",
    heroBody:
      "Bu platforma Android ilova va web sayt uchun bir xil account, bir xil fayllar, bir xil reyting va izohlar bilan ishlaydi.",
    latest: "Oxirgi publikatsiyalar",
    top: "Top yuklanmalar",
    categories: "Kategoriyalar",
    news: "Yangiliklar",
    login: "Kirish",
    register: "Ro'yxatdan o'tish",
    logout: "Chiqish",
    profile: "Profil",
    upload: "Fayl yuklash",
    uploadNow: "Publikatsiya qilish",
    search: "Qidiruv",
    email: "Email",
    password: "Parol",
    nickname: "Nik",
    avatar: "Avatar URL",
    save: "Saqlash",
    comments: "Izohlar",
    send: "Yuborish",
    downloads: "yuklanma",
    noFiles: "Hali fayl yo'q",
    siteReady: "Sayt VDS uchun tayyor",
    appReady: "Android bilan bog'lanishga tayyor",
    smartDesign: "Play Market uslubidagi dizayn",
    detail: "Batafsil",
    category: "Kategoriya",
    description: "Tavsif",
    version: "Versiya",
    size: "Hajmi",
    link: "Tashqi link",
    pickFile: "Asosiy fayl",
    pickCover: "Rasm",
    authHint: "Email va parol orqali bitta umumiy akkaunt ishlaydi.",
    uploadHint: "Yuklangan fayllar sayt va Android ilovada bir xil ko'rinadi.",
    searchHint: "Fayl, kategoriya yoki muallif qidiring"
  },
  ru: {
    brand: "SAMP STORE",
    owner: "SAMP STUDIO & AZIZ",
    tagline: "Единая платформа для SAMP, launcher, APK и mod файлов",
    heroTitle: "Один backend и одна база для сайта и Android",
    heroBody:
      "Платформа работает с общими аккаунтами, общими файлами, рейтингами и комментариями для сайта и Android-приложения.",
    latest: "Последние публикации",
    top: "Популярные загрузки",
    categories: "Категории",
    news: "Новости",
    login: "Войти",
    register: "Регистрация",
    logout: "Выйти",
    profile: "Профиль",
    upload: "Загрузка файла",
    uploadNow: "Опубликовать",
    search: "Поиск",
    email: "Email",
    password: "Пароль",
    nickname: "Ник",
    avatar: "URL аватара",
    save: "Сохранить",
    comments: "Комментарии",
    send: "Отправить",
    downloads: "загрузок",
    noFiles: "Файлов пока нет",
    siteReady: "Сайт готов для VDS",
    appReady: "Готово к подключению Android",
    smartDesign: "Дизайн в стиле Play Market",
    detail: "Подробнее",
    category: "Категория",
    description: "Описание",
    version: "Версия",
    size: "Размер",
    link: "Внешняя ссылка",
    pickFile: "Основной файл",
    pickCover: "Обложка",
    authHint: "Один общий аккаунт работает через email и пароль.",
    uploadHint: "Загруженные файлы одинаково видны на сайте и в Android-приложении.",
    searchHint: "Поиск по файлам, категориям или автору"
  }
};

const tokenKey = "samp-store-token";
const localeKey = "samp-store-locale";

export default function App() {
  const [locale, setLocale] = useState(localStorage.getItem(localeKey) || "uz");
  const [home, setHome] = useState(null);
  const [files, setFiles] = useState([]);
  const [selectedFile, setSelectedFile] = useState(null);
  const [token, setToken] = useState(localStorage.getItem(tokenKey) || "");
  const [profile, setProfile] = useState(null);
  const [authMode, setAuthMode] = useState("login");
  const [search, setSearch] = useState("");
  const [message, setMessage] = useState("");
  const [comment, setComment] = useState("");
  const [authForm, setAuthForm] = useState({ email: "", password: "", nickname: "" });
  const [profileForm, setProfileForm] = useState({ nickname: "", avatarUrl: "" });
  const [uploadForm, setUploadForm] = useState({
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
      .catch((error) => setMessage(error.message));
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
      .catch((error) => {
        setMessage(error.message);
        setToken("");
        localStorage.removeItem(tokenKey);
      });
  }, [token]);

  const filteredFiles = useMemo(() => {
    const value = search.trim().toLowerCase();
    if (!value) return files;
    return files.filter((file) =>
      `${file.title} ${file.description} ${file.authorName} ${file.category}`.toLowerCase().includes(value)
    );
  }, [files, search]);

  async function refresh(selectedId) {
    const [homeData, fileData] = await Promise.all([api.home(), api.files()]);
    setHome(homeData);
    setFiles(fileData);
    setSelectedFile(fileData.find((item) => item.id === selectedId) || fileData[0] || null);
  }

  async function submitAuth(event) {
    event.preventDefault();
    setMessage("");
    try {
      const result =
        authMode === "login"
          ? await api.login(authForm)
          : await api.register(authForm);
      localStorage.setItem(tokenKey, result.token);
      setToken(result.token);
      setAuthForm({ email: "", password: "", nickname: "" });
    } catch (error) {
      setMessage(error.message);
    }
  }

  async function saveProfile(event) {
    event.preventDefault();
    try {
      const updated = await api.updateProfile(token, profileForm);
      setProfile((current) => ({ ...current, ...updated }));
      await refresh(selectedFile?.id);
      setMessage(t.save);
    } catch (error) {
      setMessage(error.message);
    }
  }

  async function submitUpload(event) {
    event.preventDefault();
    try {
      const formData = new FormData();
      Object.entries(uploadForm).forEach(([key, value]) => {
        if (value) formData.append(key, value);
      });
      const created = await api.uploadFile(token, formData);
      await refresh(created.id);
      setUploadForm({
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
      setMessage(t.uploadHint);
    } catch (error) {
      setMessage(error.message);
    }
  }

  async function submitComment(event) {
    event.preventDefault();
    if (!selectedFile || !comment.trim()) return;
    try {
      await api.addComment(token, selectedFile.id, comment.trim());
      setComment("");
      await refresh(selectedFile.id);
    } catch (error) {
      setMessage(error.message);
    }
  }

  async function rateFile(fileId, value) {
    try {
      await api.addRating(token, fileId, value);
      await refresh(fileId);
    } catch (error) {
      setMessage(error.message);
    }
  }

  async function openFile(file) {
    await api.addDownload(file.id);
    window.open(file.fileUrl || file.downloadUrl, "_blank");
    refresh(file.id);
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
            <p className="eyebrow">{t.owner}</p>
            <h1>{t.heroTitle}</h1>
            <p>{t.heroBody}</p>
            <div className="hero-actions">
              <a href="#catalog" className="button-primary">{t.latest}</a>
              <a href="#upload" className="button-secondary">{t.upload}</a>
            </div>
          </div>
          <div className="hero-panel">
            <div className="stat-card">
              <span>{t.categories}</span>
              <strong>{home?.categories?.length || 0}</strong>
            </div>
            <div className="stat-card">
              <span>{t.latest}</span>
              <strong>{home?.stats?.totalFiles || 0}</strong>
            </div>
            <div className="stat-card">
              <span>Users</span>
              <strong>{home?.stats?.totalUsers || 0}</strong>
            </div>
          </div>
        </section>

        <section className="discover-grid">
          <article className="editorial-card"><h3>{t.smartDesign}</h3><p>{t.heroBody}</p></article>
          <article className="editorial-card accent"><h3>{t.siteReady}</h3><p>Debian VDS, domen va API bilan ishlash uchun tayyor.</p></article>
          <article className="editorial-card cool"><h3>{t.appReady}</h3><p>{t.uploadHint}</p></article>
        </section>

        <section className="content-grid">
          <div className="main-column">
            <div className="section-heading" id="catalog">
              <h2>{t.latest}</h2>
            </div>
            <div className="search-box">
              <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder={t.searchHint} />
            </div>
            <div className="cards-grid">
              {filteredFiles.length ? filteredFiles.map((file) => (
                <article key={file.id} className={`file-card ${selectedFile?.id === file.id ? "active" : ""}`} onClick={() => setSelectedFile(file)}>
                  <div className="cover">{file.coverUrl ? <img src={file.coverUrl} alt={file.title} /> : <span>{file.title[0]}</span>}</div>
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
              )) : <div className="empty-card">{t.noFiles}</div>}
            </div>
          </div>

          <aside className="side-column">
            <section className="panel">
              <div className="section-heading"><h2>{t.top}</h2></div>
              <div className="mini-list">
                {(home?.topFiles || []).map((file) => (
                  <button key={file.id} className="mini-item" onClick={() => setSelectedFile(file)}>
                    <span>{file.title}</span>
                    <strong>{file.downloads}</strong>
                  </button>
                ))}
              </div>
            </section>
            <section className="panel">
              <div className="section-heading"><h2>{t.news}</h2></div>
              <div className="news-list">
                {(home?.news || []).map((item) => (
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
            <div className="section-heading"><h2>{selectedFile?.title || t.detail}</h2></div>
            {selectedFile ? (
              <>
                <p className="detail-description">{selectedFile.description}</p>
                <div className="detail-meta">
                  <span>{t.category}: {selectedFile.category}</span>
                  <span>{t.version}: {selectedFile.version}</span>
                  <span>{t.size}: {selectedFile.size}</span>
                  <span>{selectedFile.rating || 0}★</span>
                </div>
                <div className="rate-row">
                  {[1, 2, 3, 4, 5].map((value) => (
                    <button key={value} className="rate-button" onClick={() => rateFile(selectedFile.id, value)}>
                      {value}★
                    </button>
                  ))}
                </div>
                <div className="hero-actions">
                  <button className="button-primary" onClick={() => openFile(selectedFile)}>{t.detail}</button>
                </div>
                <div className="comment-section">
                  <h3>{t.comments}</h3>
                  <form onSubmit={submitComment} className="comment-form">
                    <textarea value={comment} onChange={(event) => setComment(event.target.value)} />
                    <button className="button-secondary" type="submit">{t.send}</button>
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
                <button className={authMode === "login" ? "tab active" : "tab"} onClick={() => setAuthMode("login")}>{t.login}</button>
                <button className={authMode === "register" ? "tab active" : "tab"} onClick={() => setAuthMode("register")}>{t.register}</button>
              </div>
              {profile ? (
                <form onSubmit={saveProfile} className="form-grid">
                  <h3>{t.profile}</h3>
                  <input value={profileForm.nickname} onChange={(event) => setProfileForm((current) => ({ ...current, nickname: event.target.value }))} placeholder={t.nickname} />
                  <input value={profileForm.avatarUrl} onChange={(event) => setProfileForm((current) => ({ ...current, avatarUrl: event.target.value }))} placeholder={t.avatar} />
                  <button className="button-primary" type="submit">{t.save}</button>
                </form>
              ) : (
                <form onSubmit={submitAuth} className="form-grid">
                  <h3>{authMode === "login" ? t.login : t.register}</h3>
                  <p>{t.authHint}</p>
                  {authMode === "register" ? (
                    <input value={authForm.nickname} onChange={(event) => setAuthForm((current) => ({ ...current, nickname: event.target.value }))} placeholder={t.nickname} />
                  ) : null}
                  <input type="email" value={authForm.email} onChange={(event) => setAuthForm((current) => ({ ...current, email: event.target.value }))} placeholder={t.email} />
                  <input type="password" value={authForm.password} onChange={(event) => setAuthForm((current) => ({ ...current, password: event.target.value }))} placeholder={t.password} />
                  <button className="button-primary" type="submit">{authMode === "login" ? t.login : t.register}</button>
                </form>
              )}
            </section>

            <section className="panel upload-panel" id="upload">
              <form onSubmit={submitUpload} className="form-grid">
                <h3>{t.upload}</h3>
                <input value={uploadForm.title} onChange={(event) => setUploadForm((current) => ({ ...current, title: event.target.value }))} placeholder="Title" />
                <textarea value={uploadForm.description} onChange={(event) => setUploadForm((current) => ({ ...current, description: event.target.value }))} placeholder={t.description} />
                <select value={uploadForm.category} onChange={(event) => setUploadForm((current) => ({ ...current, category: event.target.value }))}>
                  {(home?.categories || []).map((category) => <option key={category.id} value={category.slug}>{category.name}</option>)}
                </select>
                <input value={uploadForm.version} onChange={(event) => setUploadForm((current) => ({ ...current, version: event.target.value }))} placeholder={t.version} />
                <input value={uploadForm.size} onChange={(event) => setUploadForm((current) => ({ ...current, size: event.target.value }))} placeholder={t.size} />
                <input value={uploadForm.downloadUrl} onChange={(event) => setUploadForm((current) => ({ ...current, downloadUrl: event.target.value }))} placeholder={t.link} />
                <label className="file-label"><span>{t.pickFile}</span><input type="file" onChange={(event) => setUploadForm((current) => ({ ...current, file: event.target.files[0] }))} /></label>
                <label className="file-label"><span>{t.pickCover}</span><input type="file" accept="image/*" onChange={(event) => setUploadForm((current) => ({ ...current, cover: event.target.files[0] }))} /></label>
                <button className="button-primary" type="submit">{t.uploadNow}</button>
              </form>
            </section>
          </div>
        </section>

        {message ? <div className="toast">{message}</div> : null}
      </main>
    </div>
  );
}
