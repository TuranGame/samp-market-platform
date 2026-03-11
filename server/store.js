import fs from "fs";
import path from "path";
import { nanoid } from "nanoid";

const DATA_DIR = process.env.DATA_DIR
  ? path.resolve(process.env.DATA_DIR)
  : path.resolve(process.cwd(), "data");
const STORE_PATH = path.join(DATA_DIR, "store.json");
const UPLOAD_DIR = path.join(DATA_DIR, "uploads");

const seedData = {
  users: [],
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
        uz: "SAMP MARKET platformasi ishga tushdi",
        ru: "Платформа SAMP MARKET запущена"
      },
      body: {
        uz: "Sayt va API endi bir joydan ishlaydi. Android ilova keyingi bosqichda shu backendga ulanadi.",
        ru: "Сайт и API теперь работают из одного места. Android-приложение на следующем этапе будет подключено к этому backend."
      },
      createdAt: new Date().toISOString()
    },
    {
      id: nanoid(),
      title: {
        uz: "Render uchun deploy fayllari tayyor",
        ru: "Файлы для деплоя на Render готовы"
      },
      body: {
        uz: "Persistent disk bilan yuklangan fayllar va ma'lumotlar saqlanadi.",
        ru: "С persistent disk будут сохраняться загруженные файлы и данные."
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
      authorName: "SAMP Studio",
      version: "1.0.0",
      downloadUrl: "https://example.com/turan-launcher.apk",
      coverUrl: "",
      fileUrl: "",
      size: "26 MB",
      comments: [],
      ratings: [],
      downloads: 1260,
      createdAt: new Date().toISOString()
    },
    {
      id: nanoid(),
      title: "Arizona Neon HUD",
      description: "Yorqin, zamonaviy HUD va mod pack.",
      category: "mods",
      language: "all",
      authorId: "system",
      authorName: "SAMP Studio",
      version: "2.4.1",
      downloadUrl: "https://example.com/arizona-neon.zip",
      coverUrl: "",
      fileUrl: "",
      size: "41 MB",
      comments: [],
      ratings: [],
      downloads: 820,
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
  return JSON.parse(fs.readFileSync(STORE_PATH, "utf8"));
}

export function writeStore(data) {
  ensureDataLayer();
  fs.writeFileSync(STORE_PATH, JSON.stringify(data, null, 2));
}
