import fs from "fs";
import path from "path";
import { nanoid } from "nanoid";

const DATA_DIR = process.env.DATA_DIR
  ? path.resolve(process.env.DATA_DIR)
  : path.resolve(process.cwd(), "data");
const STORE_PATH = path.join(DATA_DIR, "store.json");
const UPLOAD_DIR = path.join(DATA_DIR, "uploads");

const seedData = {
  meta: {
    brand: "SAMP STORE",
    owner: "SAMP STUDIO & AZIZ"
  },
  users: [],
  notifications: [],
  messages: [],
  categories: [
    { id: "cat-apk", name: "APK", slug: "apk", accent: "#1e8e63" },
    { id: "cat-launcher", name: "Launcher", slug: "launcher", accent: "#f39c4a" },
    { id: "cat-mods", name: "Mods", slug: "mods", accent: "#205b9f" },
    { id: "cat-guides", name: "Guides", slug: "guides", accent: "#a34fd8" }
  ],
  news: [
    {
      id: nanoid(),
      title: {
        uz: "SAMP STORE platformasi ishga tushdi",
        ru: "Платформа SAMP STORE запущена"
      },
      body: {
        uz: "Sayt, Android ilova va account tizimi endi bitta backend orqali ishlaydi.",
        ru: "Сайт, Android-приложение и система аккаунтов теперь работают через единый backend."
      },
      createdAt: new Date().toISOString()
    },
    {
      id: nanoid(),
      title: {
        uz: "Yagona baza va fayl markazi tayyor",
        ru: "Единая база и центр файлов готовы"
      },
      body: {
        uz: "Publikatsiyalar, reytinglar va izohlar sayt hamda ilova o'rtasida umumiy bo'ladi.",
        ru: "Публикации, рейтинги и комментарии будут общими для сайта и приложения."
      },
      createdAt: new Date(Date.now() - 86400000).toISOString()
    }
  ],
  files: [
    {
      id: nanoid(),
      title: "Turan RP Launcher",
      description: "SAMP serveriga kirish uchun launcher va asosiy fayllar to'plami.",
      category: "launcher",
      language: "all",
      authorId: "system",
      authorName: "SAMP STUDIO & AZIZ",
      version: "1.0.0",
      downloadUrl: "https://turan-rp.uz",
      coverUrl: "",
      fileUrl: "",
      size: "26 MB",
      comments: [],
      ratings: [],
      downloads: 1260,
      status: "approved",
      createdAt: new Date().toISOString()
    },
    {
      id: nanoid(),
      title: "Arizona Neon HUD",
      description: "Yorqin, zamonaviy HUD va mod pack.",
      category: "mods",
      language: "all",
      authorId: "system",
      authorName: "SAMP STUDIO & AZIZ",
      version: "2.4.1",
      downloadUrl: "https://turan-rp.uz",
      coverUrl: "",
      fileUrl: "",
      size: "41 MB",
      comments: [],
      ratings: [],
      downloads: 820,
      status: "approved",
      createdAt: new Date(Date.now() - 7200000).toISOString()
    }
  ]
};

export function ensureDataLayer() {
  fs.mkdirSync(DATA_DIR, { recursive: true });
  fs.mkdirSync(UPLOAD_DIR, { recursive: true });
  if (!fs.existsSync(STORE_PATH)) {
    fs.writeFileSync(STORE_PATH, JSON.stringify(seedData, null, 2));
  }
}

export function getDataPaths() {
  return {
    DATA_DIR,
    STORE_PATH,
    UPLOAD_DIR
  };
}

export function readStore() {
  ensureDataLayer();
  const parsed = JSON.parse(fs.readFileSync(STORE_PATH, "utf8"));
  if (!parsed.meta) {
    parsed.meta = seedData.meta;
  }
  parsed.notifications ??= [];
  parsed.messages ??= [];
  parsed.users ??= [];
  parsed.news ??= [];
  parsed.files ??= [];
  parsed.categories ??= [];
  parsed.users = parsed.users.map((user) => ({
    avatarUrl: "",
    username: user.username || user.nickname || String(user.email || "").split("@")[0] || `user-${nanoid(6)}`,
    role: user.role || "user",
    developerApproved: user.role === "developer" ? Boolean(user.developerApproved) : true,
    canChat: user.canChat !== false,
    canComment: user.canComment !== false,
    canUpload: user.canUpload !== false,
    blockedUntil: user.blockedUntil || "",
    blockReason: user.blockReason || "",
    lastSeenAt: user.lastSeenAt || user.createdAt || new Date().toISOString(),
    downloadHistory: Array.isArray(user.downloadHistory) ? user.downloadHistory : [],
    ...user
  }));
  parsed.files = parsed.files.map((file) => ({
    status: file.status || "approved",
    ratings: file.ratings || [],
    comments: file.comments || [],
    downloads: file.downloads || 0,
    downloadUrl: file.downloadUrl || "",
    fileType: file.fileType || "file",
    packageName: file.packageName || "",
    coverUrl: file.coverUrl || "",
    fileUrl: file.fileUrl || "",
    screenshotUrls: Array.isArray(file.screenshotUrls) ? file.screenshotUrls : [],
    scanStatus: file.scanStatus || "clean",
    scanEngine: file.scanEngine || "SAMP Shield",
    isSafe: file.isSafe !== false,
    ...file
  }));
  parsed.messages = parsed.messages.map((message) => ({
    status: message.status || "sent",
    deliveredAt: message.deliveredAt || null,
    readAt: message.readAt || null,
    deletedFor: Array.isArray(message.deletedFor) ? message.deletedFor : [],
    ...message
  }));
  parsed.notifications = parsed.notifications.map((notification) => ({
    title: notification.title || "",
    chatUserId: notification.chatUserId || "",
    createdAt: notification.createdAt || new Date().toISOString(),
    ...notification
  }));
  return parsed;
}

export function writeStore(data) {
  ensureDataLayer();
  fs.writeFileSync(STORE_PATH, JSON.stringify(data, null, 2));
}
