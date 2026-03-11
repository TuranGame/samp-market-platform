import express from "express";
import cors from "cors";
import path from "path";
import fs from "fs";
import multer from "multer";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import { nanoid } from "nanoid";
import { ensureDataLayer, getDataPaths, readStore, writeStore } from "./store.js";

const app = express();
const PORT = Number(process.env.PORT || 3000);
const JWT_SECRET = process.env.JWT_SECRET || "change-me";
const { UPLOAD_DIR } = getDataPaths();

ensureDataLayer();

const storage = multer.diskStorage({
  destination: (_req, _file, cb) => cb(null, UPLOAD_DIR),
  filename: (_req, file, cb) => {
    const safeName = file.originalname.replace(/\s+/g, "-").replace(/[^\w.-]/g, "");
    cb(null, `${Date.now()}-${safeName}`);
  }
});

const upload = multer({ storage });

app.use(cors());
app.use(express.json({ limit: "10mb" }));
app.use("/uploads", express.static(UPLOAD_DIR));

function publicUser(user) {
  return {
    id: user.id,
    email: user.email,
    nickname: user.nickname,
    avatarUrl: user.avatarUrl,
    createdAt: user.createdAt
  };
}

function getLanguage(req) {
  return req.headers["x-lang"] === "ru" ? "ru" : "uz";
}

function getToken(req) {
  const value = req.headers.authorization || "";
  return value.startsWith("Bearer ") ? value.slice(7) : null;
}

function requireAuth(req, res, next) {
  const token = getToken(req);
  if (!token) {
    return res.status(401).json({ message: "Auth token required" });
  }

  try {
    req.auth = jwt.verify(token, JWT_SECRET);
    next();
  } catch {
    res.status(401).json({ message: "Invalid token" });
  }
}

function getUserFromStore(store, userId) {
  return store.users.find((user) => user.id === userId);
}

function formatFile(file) {
  const ratingCount = file.ratings.length;
  const rating = ratingCount
    ? Number((file.ratings.reduce((sum, item) => sum + item.value, 0) / ratingCount).toFixed(1))
    : 0;

  return {
    ...file,
    rating,
    ratingCount,
    commentsCount: file.comments.length
  };
}

function createToken(user) {
  return jwt.sign(
    {
      sub: user.id,
      email: user.email
    },
    JWT_SECRET,
    { expiresIn: "14d" }
  );
}

app.get("/api/health", (_req, res) => {
  res.json({ ok: true, status: "running" });
});

app.get("/api/config", (req, res) => {
  res.json({
    apiBaseUrl: `${req.protocol}://${req.get("host")}/api`,
    uploadsBaseUrl: `${req.protocol}://${req.get("host")}/uploads`
  });
});

app.post("/api/auth/register", async (req, res) => {
  const { email, password, nickname } = req.body;
  if (!email || !password || !nickname) {
    return res.status(400).json({ message: "email, password, nickname required" });
  }

  const store = readStore();
  const exists = store.users.some((user) => user.email.toLowerCase() === String(email).toLowerCase());
  if (exists) {
    return res.status(409).json({ message: "Email already registered" });
  }

  const user = {
    id: nanoid(),
    email,
    nickname,
    avatarUrl: "",
    passwordHash: await bcrypt.hash(password, 10),
    createdAt: new Date().toISOString()
  };

  store.users.push(user);
  writeStore(store);

  res.status(201).json({
    token: createToken(user),
    user: publicUser(user)
  });
});

app.post("/api/auth/login", async (req, res) => {
  const { email, password } = req.body;
  const store = readStore();
  const user = store.users.find((item) => item.email.toLowerCase() === String(email || "").toLowerCase());

  if (!user || !(await bcrypt.compare(password || "", user.passwordHash))) {
    return res.status(401).json({ message: "Invalid credentials" });
  }

  res.json({
    token: createToken(user),
    user: publicUser(user)
  });
});

app.get("/api/home", (req, res) => {
  const store = readStore();
  const latestFiles = store.files
    .slice()
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    .slice(0, 8)
    .map(formatFile);
  const topFiles = store.files
    .slice()
    .sort((a, b) => b.downloads - a.downloads)
    .slice(0, 6)
    .map(formatFile);

  res.json({
    locale: getLanguage(req),
    stats: {
      totalFiles: store.files.length,
      totalUsers: store.users.length,
      totalNews: store.news.length
    },
    categories: store.categories,
    latestFiles,
    topFiles,
    news: store.news.slice().sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)).slice(0, 6)
  });
});

app.get("/api/categories", (_req, res) => {
  const store = readStore();
  res.json(store.categories);
});

app.get("/api/files", (req, res) => {
  const { category, q } = req.query;
  const store = readStore();
  let files = store.files.slice();

  if (category) {
    files = files.filter((file) => file.category === category);
  }
  if (q) {
    const value = String(q).toLowerCase();
    files = files.filter(
      (file) =>
        file.title.toLowerCase().includes(value) ||
        file.description.toLowerCase().includes(value) ||
        file.authorName.toLowerCase().includes(value)
    );
  }

  res.json(files.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)).map(formatFile));
});

app.get("/api/files/:id", (req, res) => {
  const store = readStore();
  const file = store.files.find((item) => item.id === req.params.id);

  if (!file) {
    return res.status(404).json({ message: "File not found" });
  }

  res.json(formatFile(file));
});

app.post(
  "/api/files/upload",
  requireAuth,
  upload.fields([
    { name: "file", maxCount: 1 },
    { name: "cover", maxCount: 1 }
  ]),
  (req, res) => {
    const store = readStore();
    const user = getUserFromStore(store, req.auth.sub);
    if (!user) {
      return res.status(401).json({ message: "User not found" });
    }

    const payload = req.body;
    if (!payload.title || !payload.description || !payload.category) {
      return res.status(400).json({ message: "title, description and category are required" });
    }

    const uploadedFile = req.files?.file?.[0];
    const cover = req.files?.cover?.[0];

    const item = {
      id: nanoid(),
      title: payload.title,
      description: payload.description,
      category: payload.category,
      language: payload.language || "all",
      authorId: user.id,
      authorName: user.nickname,
      version: payload.version || "1.0.0",
      downloadUrl: payload.downloadUrl || "",
      coverUrl: cover ? `/uploads/${cover.filename}` : "",
      fileUrl: uploadedFile ? `/uploads/${uploadedFile.filename}` : "",
      size: payload.size || (uploadedFile ? `${Math.ceil(uploadedFile.size / 1024 / 1024)} MB` : "0 MB"),
      comments: [],
      ratings: [],
      downloads: 0,
      createdAt: new Date().toISOString()
    };

    store.files.unshift(item);
    writeStore(store);
    res.status(201).json(formatFile(item));
  }
);

app.post("/api/files/:id/comments", requireAuth, (req, res) => {
  const { message } = req.body;
  if (!message) {
    return res.status(400).json({ message: "message required" });
  }

  const store = readStore();
  const user = getUserFromStore(store, req.auth.sub);
  const file = store.files.find((item) => item.id === req.params.id);

  if (!user || !file) {
    return res.status(404).json({ message: "File or user not found" });
  }

  const comment = {
    id: nanoid(),
    authorId: user.id,
    authorName: user.nickname,
    message,
    createdAt: new Date().toISOString()
  };

  file.comments.unshift(comment);
  writeStore(store);
  res.status(201).json(comment);
});

app.post("/api/files/:id/rating", requireAuth, (req, res) => {
  const value = Number(req.body.value);
  if (!Number.isFinite(value) || value < 1 || value > 5) {
    return res.status(400).json({ message: "value must be between 1 and 5" });
  }

  const store = readStore();
  const file = store.files.find((item) => item.id === req.params.id);
  if (!file) {
    return res.status(404).json({ message: "File not found" });
  }

  const existing = file.ratings.find((item) => item.authorId === req.auth.sub);
  if (existing) {
    existing.value = value;
  } else {
    file.ratings.push({ authorId: req.auth.sub, value });
  }

  writeStore(store);
  res.json(formatFile(file));
});

app.post("/api/files/:id/download", (req, res) => {
  const store = readStore();
  const file = store.files.find((item) => item.id === req.params.id);
  if (!file) {
    return res.status(404).json({ message: "File not found" });
  }

  file.downloads += 1;
  writeStore(store);
  res.json({ downloads: file.downloads });
});

app.get("/api/profile", requireAuth, (req, res) => {
  const store = readStore();
  const user = getUserFromStore(store, req.auth.sub);
  if (!user) {
    return res.status(404).json({ message: "User not found" });
  }

  const uploads = store.files.filter((file) => file.authorId === user.id).map(formatFile);
  res.json({
    ...publicUser(user),
    uploads
  });
});

app.put("/api/profile", requireAuth, (req, res) => {
  const store = readStore();
  const user = getUserFromStore(store, req.auth.sub);
  if (!user) {
    return res.status(404).json({ message: "User not found" });
  }

  user.nickname = req.body.nickname || user.nickname;
  user.avatarUrl = req.body.avatarUrl || user.avatarUrl;
  writeStore(store);

  store.files.forEach((file) => {
    if (file.authorId === user.id) {
      file.authorName = user.nickname;
    }
  });
  writeStore(store);

  res.json(publicUser(user));
});

const clientDist = path.resolve(process.cwd(), "dist", "client");
if (fs.existsSync(clientDist)) {
  app.use(express.static(clientDist));
  app.get("*", (req, res, next) => {
    if (req.path.startsWith("/api")) {
      return next();
    }
    res.sendFile(path.join(clientDist, "index.html"));
  });
}

app.listen(PORT, () => {
  console.log(`SAMP MARKET server running on http://localhost:${PORT}`);
});
