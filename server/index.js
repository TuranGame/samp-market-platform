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
const MAX_UPLOAD_BYTES = 200 * 1024 * 1024;
const MAX_AVATAR_BYTES = 5 * 1024 * 1024;
const MAX_SCREENSHOT_BYTES = 1 * 1024 * 1024;
const ADMIN_USERNAME = "Dalerdev";
const ADMIN_NICKNAME = "Daler_Baltaev";
const ADMIN_PASSWORD = "sadulloev";
const ADMIN_EMAIL = "boltaev.04.1993@mail.ru";

ensureDataLayer();

function fileExt(name = "") {
  return path.extname(name).replace(".", "").toLowerCase();
}

function inferFileType(name = "") {
  const ext = fileExt(name);
  if (ext === "apk") return "apk";
  if (["zip", "rar", "7z"].includes(ext)) return "archive";
  return "file";
}

function safeUnlink(filePath) {
  if (!filePath) return;
  try {
    fs.unlinkSync(filePath);
  } catch {}
}

function cleanupUploaded(files = []) {
  files.flat().forEach((file) => safeUnlink(file?.path));
}

function resolveUploadPath(publicPath = "") {
  const normalized = String(publicPath || "").trim();
  if (!normalized) return "";
  try {
    const parsed = normalized.startsWith("http://") || normalized.startsWith("https://")
      ? new URL(normalized)
      : null;
    const pathname = parsed ? parsed.pathname : normalized;
    if (!pathname.startsWith("/uploads/")) return "";
    return path.join(UPLOAD_DIR, path.basename(pathname));
  } catch {
    if (!normalized.startsWith("/uploads/")) return "";
    return path.join(UPLOAD_DIR, path.basename(normalized));
  }
}

function deleteFileAssets(file) {
  [
    file?.fileUrl,
    file?.coverUrl,
    file?.iconUrl,
    ...(Array.isArray(file?.screenshotUrls) ? file.screenshotUrls : [])
  ].forEach((asset) => {
    const localPath = resolveUploadPath(asset);
    if (localPath) safeUnlink(localPath);
  });
}

function deleteAvatarAsset(user) {
  const avatarPath = resolveUploadPath(user?.avatarUrl);
  if (avatarPath) {
    safeUnlink(avatarPath);
  }
}

const storage = multer.diskStorage({
  destination: (_req, _file, cb) => cb(null, UPLOAD_DIR),
  filename: (_req, file, cb) => {
    const safeName = file.originalname.replace(/\s+/g, "-").replace(/[^\w.-]/g, "");
    cb(null, `${Date.now()}-${safeName}`);
  }
});

const upload = multer({
  storage,
  limits: { fileSize: MAX_UPLOAD_BYTES },
  fileFilter: (_req, file, cb) => {
    const ext = fileExt(file.originalname);
    if (file.fieldname === "file") {
      if (["apk", "zip", "rar", "7z"].includes(ext)) {
        return cb(null, true);
      }
      return cb(new Error("Only APK, ZIP, RAR or 7Z files are allowed"));
    }
    if (file.fieldname === "cover") {
      if (["png", "jpg", "jpeg", "webp"].includes(ext)) {
        return cb(null, true);
      }
      return cb(new Error("Cover must be PNG, JPG, JPEG or WEBP"));
    }
    if (file.fieldname === "screenshots" || file.fieldname === "icon") {
      if (["png", "jpg", "jpeg", "webp"].includes(ext)) {
        return cb(null, true);
      }
      return cb(new Error("Images must be PNG, JPG, JPEG or WEBP"));
    }
    return cb(new Error("Unexpected file field"));
  }
});

const avatarUpload = multer({
  storage,
  limits: { fileSize: MAX_AVATAR_BYTES },
  fileFilter: (_req, file, cb) => {
    const ext = fileExt(file.originalname);
    if (["png", "jpg", "jpeg", "webp"].includes(ext)) {
      return cb(null, true);
    }
    return cb(new Error("Avatar must be PNG, JPG, JPEG or WEBP"));
  }
});

app.use(cors());
app.use(express.json({ limit: "10mb" }));
app.use("/uploads", express.static(UPLOAD_DIR));

function getLanguage(req) {
  return req.headers["x-lang"] === "ru" ? "ru" : "uz";
}

function getToken(req) {
  const value = req.headers.authorization || "";
  return value.startsWith("Bearer ") ? value.slice(7) : null;
}

function getOptionalAuth(req) {
  const token = getToken(req);
  if (!token) return null;
  try {
    return jwt.verify(token, JWT_SECRET);
  } catch {
    return null;
  }
}

function createToken(user) {
  return jwt.sign(
    {
      sub: user.id,
      email: user.email,
      username: user.username,
      role: user.role
    },
    JWT_SECRET,
    { expiresIn: "14d" }
  );
}

function publicUser(user) {
  const username = user.username || user.nickname || String(user.email || "").split("@")[0];
  const role = user.role || "user";
  const isOnline = user.lastSeenAt
    ? Date.now() - new Date(user.lastSeenAt).getTime() < 2 * 60 * 1000
    : false;
  const blocked = isUserBlocked(user);
  return {
    id: user.id,
    email: user.email,
    username,
    nickname: user.nickname,
    avatarUrl: user.avatarUrl,
    role,
    badge: role === "admin" ? "ADMIN" : role === "developer" ? "DEVELOPER" : "USER",
    createdAt: user.createdAt,
    isOnline,
    lastSeenAt: user.lastSeenAt || user.createdAt,
    developerApproved: user.role === "developer" ? Boolean(user.developerApproved) : true,
    canChat: user.canChat !== false,
    canComment: user.canComment !== false,
    canUpload: user.canUpload !== false,
    isBlocked: blocked,
    blockReason: user.blockReason || "",
    blockedUntil: user.blockedUntil || ""
  };
}

function isOwnerAdmin(user) {
  return user?.role === "admin" && (
    user.username === ADMIN_USERNAME ||
    user.email === ADMIN_EMAIL ||
    user.nickname === ADMIN_NICKNAME
  );
}

function ensureAdminUser(store) {
  const adminMatches = store.users.filter((user) =>
    [user.username, user.nickname, user.email].includes(ADMIN_USERNAME) ||
    [user.username, user.nickname, user.email].includes(ADMIN_NICKNAME) ||
    [user.username, user.nickname, user.email].includes(ADMIN_EMAIL) ||
    [user.username, user.nickname, user.email].includes("admin@sampstore.local") ||
    [user.username, user.nickname].includes("Daler_Blataev") ||
    [user.username, user.nickname].includes("Daler_Baltaev")
  );
  const exists = adminMatches.find((user) => user.username === ADMIN_USERNAME)
    || adminMatches.find((user) => user.email === ADMIN_EMAIL)
    || adminMatches.find((user) => user.nickname === ADMIN_NICKNAME)
    || adminMatches[0];
  if (!exists) {
    store.users.push({
      id: nanoid(),
      email: ADMIN_EMAIL,
      username: ADMIN_USERNAME,
      nickname: ADMIN_NICKNAME,
      avatarUrl: "",
      role: "admin",
      developerApproved: true,
      canChat: true,
      canComment: true,
      canUpload: true,
      blockedUntil: "",
      blockReason: "",
      passwordHash: bcrypt.hashSync(ADMIN_PASSWORD, 10),
      createdAt: new Date().toISOString(),
      lastSeenAt: new Date().toISOString()
    });
  } else {
    const duplicateAdmins = adminMatches.filter((user) => user.id !== exists.id);
    duplicateAdmins.forEach((duplicate) => {
      store.files.forEach((file) => {
        if (file.authorId === duplicate.id) {
          file.authorId = exists.id;
          file.authorName = exists.nickname || ADMIN_NICKNAME;
          file.authorRole = "admin";
        }
        (file.comments || []).forEach((comment) => {
          if (comment.authorId === duplicate.id) {
            comment.authorId = exists.id;
            comment.authorName = exists.nickname || ADMIN_NICKNAME;
            comment.authorRole = "admin";
          }
        });
      });
      (store.messages || []).forEach((message) => {
        if (message.fromUserId === duplicate.id) {
          message.fromUserId = exists.id;
          message.fromNickname = exists.nickname || ADMIN_NICKNAME;
        }
        if (message.toUserId === duplicate.id) {
          message.toUserId = exists.id;
          message.toNickname = exists.nickname || ADMIN_NICKNAME;
        }
      });
      (store.notifications || []).forEach((notification) => {
        if (notification.userId === duplicate.id) {
          notification.userId = exists.id;
        }
      });
      if ((duplicate.downloadHistory || []).length > 0) {
        exists.downloadHistory = Array.from(new Set([...(exists.downloadHistory || []), ...duplicate.downloadHistory]));
      }
    });
    if (duplicateAdmins.length > 0) {
      store.users = store.users.filter((user) => !duplicateAdmins.some((duplicate) => duplicate.id === user.id));
    }
    exists.email = ADMIN_EMAIL;
    exists.username = ADMIN_USERNAME;
    exists.nickname = ADMIN_NICKNAME;
    exists.role = "admin";
    exists.developerApproved = true;
    exists.canChat = true;
    exists.canComment = true;
    exists.canUpload = true;
    exists.blockedUntil = "";
    exists.blockReason = "";
    exists.passwordHash = bcrypt.hashSync(ADMIN_PASSWORD, 10);
  }
  writeStore(store);
}

function touchUserSession(store, userId) {
  const user = getUser(store, userId);
  if (!user) return null;
  user.lastSeenAt = new Date().toISOString();
  return user;
}

function requireAuth(req, res, next) {
  const token = getToken(req);
  if (!token) {
    return res.status(401).json({ message: "Auth token required" });
  }
  try {
    req.auth = jwt.verify(token, JWT_SECRET);
    const store = readStore();
    touchUserSession(store, req.auth.sub);
    writeStore(store);
    next();
  } catch {
    res.status(401).json({ message: "Invalid token" });
  }
}

function requireAdmin(req, res, next) {
  const store = readStore();
  const user = getUser(store, req.auth?.sub);
  if (user?.role !== "admin") {
    return res.status(403).json({ message: "Admin only" });
  }
  req.auth.role = user.role;
  next();
}

function requireDeveloper(req, res, next) {
  const store = readStore();
  const user = getUser(store, req.auth?.sub);
  if (!["developer", "admin"].includes(user?.role)) {
    return res.status(403).json({ message: "Only developer or admin can upload" });
  }
  req.auth.role = user.role;
  const message = ensureUserCan(user, "upload");
  if (message) {
    return res.status(403).json({ message, blocked: isUserBlocked(user), developerApproved: user?.developerApproved === true });
  }
  next();
}

function getUser(store, userId) {
  return store.users.find((user) => user.id === userId);
}

function ensureUniqueUsername(store, username, ignoreUserId = "") {
  return !store.users.some((user) => user.id !== ignoreUserId && String(user.username || "").toLowerCase() === String(username || "").toLowerCase());
}

function isUserBlocked(user) {
  if (!user?.blockedUntil) return false;
  const until = new Date(user.blockedUntil).getTime();
  if (!Number.isFinite(until)) return false;
  return until > Date.now();
}

function blockPayload(user) {
  return {
    blocked: true,
    blockReason: user.blockReason || "Admin tomonidan bloklangan",
    blockedUntil: user.blockedUntil || "",
    message: `Account bloklangan: ${user.blockReason || "Sabab ko'rsatilmagan"}`
  };
}

function ensureUserCan(user, action) {
  if (!user) return "User not found";
  if (isUserBlocked(user)) {
    return `Account bloklangan: ${user.blockReason || "Sabab ko'rsatilmagan"}`;
  }
  if (action === "chat" && user.canChat === false) {
    return "Sizga vaqtincha chat yozish taqiqlangan";
  }
  if (action === "comment" && user.canComment === false) {
    return "Sizga vaqtincha izoh yozish taqiqlangan";
  }
  if (action === "upload") {
    if (user.canUpload === false) {
      return "Sizga vaqtincha fayl yuklash taqiqlangan";
    }
    if (user.role === "developer" && !user.developerApproved) {
      return "Developer account admin tomonidan hali tasdiqlanmagan";
    }
  }
  return "";
}

function buildUserStats(store, userId) {
  const uploads = store.files.filter((file) => file.authorId === userId);
  const commentedCount = store.files.filter((file) => (file.comments || []).some((comment) => comment.authorId === userId)).length;
  const downloadedCount = getUser(store, userId)?.downloadHistory?.length || 0;
  return {
    uploadedCount: uploads.length,
    commentedCount,
    downloadedCount
  };
}

function runSecurityScan(file) {
  const ext = fileExt(file?.originalname || "");
  const suspiciousName = String(file?.originalname || "").toLowerCase();
  const suspicious = suspiciousName.includes(".apk.") || suspiciousName.includes(".zip.") || suspiciousName.includes("virus");
  const clean = ["apk", "zip", "rar", "7z"].includes(ext) && !suspicious;
  return {
    scanStatus: clean ? "clean" : "flagged",
    scanEngine: "SAMP Shield",
    isSafe: clean
  };
}

function addNotification(store, userId, kind, message, extra = {}) {
  store.notifications ??= [];
  store.notifications.unshift({
    id: nanoid(),
    userId,
    kind,
    message,
    title: extra.title || "",
    chatUserId: extra.chatUserId || "",
    read: false,
    createdAt: new Date().toISOString(),
    ...extra
  });
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

function canManageFile(user, file) {
  return Boolean(user && file && (user.role === "admin" || file.authorId === user.id));
}

function removeFileRecord(store, file) {
  if (!file) return null;
  store.files = store.files.filter((item) => item.id !== file.id);
  deleteFileAssets(file);
  store.users = store.users.map((user) => ({
    ...user,
    downloadHistory: Array.isArray(user.downloadHistory)
      ? user.downloadHistory.filter((fileId) => fileId !== file.id)
      : []
  }));
  return file;
}

function unlinkUploadAsset(publicPath, keepPaths = []) {
  const normalized = String(publicPath || "").trim();
  if (!normalized || keepPaths.includes(normalized)) return;
  const localPath = resolveUploadPath(normalized);
  if (localPath) {
    safeUnlink(localPath);
  }
}

function applyEditableStatus(file, actor, requestedStatus) {
  const nextStatus = String(requestedStatus || "").trim();
  if (!nextStatus) return;
  if (!["approved", "rejected", "pending", "inactive"].includes(nextStatus)) {
    throw new Error("Unsupported status");
  }
  if (actor.role === "admin") {
    file.status = nextStatus;
    return;
  }
  const allowedForOwner = file.status === "inactive"
    ? ["inactive", "approved"]
    : ["approved", "inactive"];
  if (!allowedForOwner.includes(nextStatus)) {
    throw new Error("You can only deactivate or reactivate your own file");
  }
  if (nextStatus === "approved" && file.isSafe === false) {
    throw new Error("Unsafe file cannot be activated");
  }
  file.status = nextStatus;
}

function deleteUserAccount(store, userId) {
  const user = getUser(store, userId);
  if (!user) return null;
  if (isOwnerAdmin(user)) {
    throw new Error("Owner admin cannot be deleted");
  }

  const deletedFileIds = new Set();
  let deletedFiles = 0;
  let deletedComments = 0;
  let deletedRatings = 0;

  store.files = store.files.filter((file) => {
    if (file.authorId !== user.id) {
      return true;
    }
    deletedFiles += 1;
    deletedComments += Array.isArray(file.comments) ? file.comments.length : 0;
    deletedRatings += Array.isArray(file.ratings) ? file.ratings.length : 0;
    deletedFileIds.add(file.id);
    deleteFileAssets(file);
    return false;
  });

  store.files.forEach((file) => {
    const commentsBefore = Array.isArray(file.comments) ? file.comments.length : 0;
    file.comments = (file.comments || []).filter((comment) => comment.authorId !== user.id);
    deletedComments += commentsBefore - file.comments.length;

    const ratingsBefore = Array.isArray(file.ratings) ? file.ratings.length : 0;
    file.ratings = (file.ratings || []).filter((rating) => rating.authorId !== user.id);
    deletedRatings += ratingsBefore - file.ratings.length;
  });

  const messagesBefore = (store.messages || []).length;
  store.messages = (store.messages || []).filter(
    (message) => message.fromUserId !== user.id && message.toUserId !== user.id
  );

  const notificationsBefore = (store.notifications || []).length;
  store.notifications = (store.notifications || []).filter(
    (notification) => notification.userId !== user.id && notification.chatUserId !== user.id
  );

  deleteAvatarAsset(user);

  store.users = store.users
    .filter((item) => item.id !== user.id)
    .map((item) => ({
      ...item,
      downloadHistory: Array.isArray(item.downloadHistory)
        ? item.downloadHistory.filter((fileId) => !deletedFileIds.has(fileId))
        : []
    }));

  return {
    deletedUserId: user.id,
    deletedUsername: user.username,
    deletedFiles,
    deletedComments,
    deletedRatings,
    deletedMessages: messagesBefore - store.messages.length,
    deletedNotifications: notificationsBefore - store.notifications.length
  };
}

function visibleFiles(store, auth) {
  return store.files.filter((file) => file.status === "approved" || auth?.role === "admin" || file.authorId === auth?.sub);
}

app.get("/api/health", (_req, res) => {
  res.json({ ok: true, status: "running" });
});

app.get("/api/home", (req, res) => {
  const store = readStore();
  ensureAdminUser(store);
  const files = visibleFiles(store);
  res.json({
    meta: store.meta,
    locale: getLanguage(req),
    stats: {
      totalFiles: files.length,
      totalUsers: store.users.length,
      totalNews: store.news.length
    },
    categories: store.categories,
    latestFiles: files.slice().sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)).slice(0, 8).map(formatFile),
    topFiles: files.slice().sort((a, b) => b.downloads - a.downloads).slice(0, 6).map(formatFile),
    news: store.news.slice().sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)).slice(0, 6)
  });
});

app.post("/api/auth/register", async (req, res) => {
  const store = readStore();
  ensureAdminUser(store);
  const { email, password, nickname, username, role } = req.body;
  const nextRole = role === "developer" ? "developer" : "user";
  const normalizedUsername = String(username || nickname || "").trim();
  const normalizedEmail = String(email || "").trim();

  if (!normalizedEmail || !password || !nickname || !normalizedUsername) {
    return res.status(400).json({ message: "email, password, nickname, username required" });
  }

  const duplicate = store.users.find(
    (user) =>
      user.email.toLowerCase() === String(email).toLowerCase() ||
      user.username.toLowerCase() === normalizedUsername.toLowerCase()
  );
  if (duplicate) {
    return res.status(409).json({ message: "Email or username already registered" });
  }

  const user = {
    id: nanoid(),
    email: normalizedEmail,
    username: normalizedUsername,
    nickname,
    avatarUrl: "",
    role: nextRole,
    developerApproved: nextRole === "developer" ? false : true,
    canChat: true,
    canComment: true,
    canUpload: true,
    blockedUntil: "",
    blockReason: "",
    passwordHash: await bcrypt.hash(password, 10),
    createdAt: new Date().toISOString(),
    lastSeenAt: new Date().toISOString()
  };
  store.users.push(user);
  writeStore(store);

  res.status(201).json({
    token: createToken(user),
    user: publicUser(user)
  });
});

app.post("/api/auth/login", async (req, res) => {
  const store = readStore();
  ensureAdminUser(store);
  const { email, password } = req.body;
  const identity = String(email || "").toLowerCase();
  const user = store.users.find(
    (item) => item.email.toLowerCase() === identity || item.username.toLowerCase() === identity
  );

  if (!user || !(await bcrypt.compare(password || "", user.passwordHash))) {
    return res.status(401).json({ message: "Invalid credentials" });
  }
  if (isUserBlocked(user)) {
    return res.status(403).json(blockPayload(user));
  }
  user.lastSeenAt = new Date().toISOString();
  writeStore(store);

  res.json({
    token: createToken(user),
    user: publicUser(user)
  });
});

app.get("/api/auth/check-identity", (req, res) => {
  const store = readStore();
  ensureAdminUser(store);
  const identity = String(req.query.identity || "").trim().toLowerCase();
  if (!identity) {
    return res.status(400).json({ message: "identity required" });
  }
  const exists = store.users.some(
    (item) => item.email.toLowerCase() === identity || item.username.toLowerCase() === identity
  );
  res.json({ exists });
});

app.get("/api/files", (req, res) => {
  const store = readStore();
  const { category, q } = req.query;
  let files = visibleFiles(store);
  if (category) {
    files = files.filter((file) => file.category === category);
  }
  if (q) {
    const value = String(q).toLowerCase();
    files = files.filter((file) =>
      `${file.title} ${file.description} ${file.authorName} ${file.category}`.toLowerCase().includes(value)
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
  requireDeveloper,
  upload.fields([
    { name: "file", maxCount: 1 },
    { name: "icon", maxCount: 1 },
    { name: "screenshots", maxCount: 3 }
  ]),
  (req, res) => {
    const store = readStore();
    const user = getUser(store, req.auth.sub);
    if (!user) {
      cleanupUploaded([Object.values(req.files || {}).flat()]);
      return res.status(401).json({ message: "User not found" });
    }
    const uploadGuard = ensureUserCan(user, "upload");
    if (uploadGuard) {
      cleanupUploaded([Object.values(req.files || {}).flat()]);
      return res.status(403).json({ message: uploadGuard, blocked: isUserBlocked(user), developerApproved: user.developerApproved === true });
    }
    const payload = req.body;
    if (!payload.title || !payload.description || !payload.category) {
      cleanupUploaded([Object.values(req.files || {}).flat()]);
      return res.status(400).json({ message: "title, description and category are required" });
    }

    const uploadedFile = req.files?.file?.[0];
    const icon = req.files?.icon?.[0];
    const screenshots = req.files?.screenshots || [];
    if (!uploadedFile) {
      cleanupUploaded([Object.values(req.files || {}).flat()]);
      return res.status(400).json({ message: "File upload is required" });
    }
    if (screenshots.length < 1 || screenshots.length > 3) {
      cleanupUploaded([uploadedFile, icon, ...screenshots]);
      return res.status(400).json({ message: "1 to 3 screenshots are required" });
    }
    if (screenshots.some((shot) => shot.size > MAX_SCREENSHOT_BYTES)) {
      cleanupUploaded([uploadedFile, icon, ...screenshots]);
      return res.status(400).json({ message: "Each screenshot must be 1 MB or smaller" });
    }
    const fileType = inferFileType(uploadedFile.originalname);
    if (fileType === "apk" && !icon) {
      cleanupUploaded([uploadedFile, ...screenshots]);
      return res.status(400).json({ message: "APK upload requires an icon" });
    }
    const detectedTitle = String(payload.detectedTitle || "").trim();
    const detectedVersion = String(payload.detectedVersion || "").trim();
    const packageName = String(payload.packageName || "").trim();
    const fallbackTitle = uploadedFile.originalname.replace(/\.[^.]+$/, "");
    const screenshotUrls = screenshots.map((item) => `/uploads/${item.filename}`);
    const coverUrl = fileType === "apk"
      ? (icon ? `/uploads/${icon.filename}` : screenshotUrls[0] || "")
      : (screenshotUrls[0] || "");
    const scan = runSecurityScan(uploadedFile);
    const item = {
      id: nanoid(),
      title: String(payload.title || detectedTitle || fallbackTitle).trim(),
      description: payload.description,
      category: payload.category,
      language: payload.language || "all",
      authorId: user.id,
      authorName: user.nickname,
      authorRole: user.role,
      version: String(payload.version || detectedVersion || "1.0.0").trim(),
      fileType,
      packageName,
      downloadUrl: payload.downloadUrl || "",
      coverUrl,
      fileUrl: uploadedFile ? `/uploads/${uploadedFile.filename}` : "",
      screenshotUrls,
      size: payload.size || (uploadedFile ? `${Math.ceil(uploadedFile.size / 1024 / 1024)} MB` : "0 MB"),
      comments: [],
      ratings: [],
      downloads: 0,
      status: scan.isSafe ? (user.role === "admin" ? "approved" : "pending") : "rejected",
      scanStatus: scan.scanStatus,
      scanEngine: scan.scanEngine,
      isSafe: scan.isSafe,
      createdAt: new Date().toISOString()
    };
    store.files.unshift(item);
    addNotification(
      store,
      user.id,
      "upload",
      !scan.isSafe
        ? "Fayl xavfsizlik tekshiruvidan o'tmadi"
        : user.role === "admin"
          ? "Fayl joylandi"
          : "Fayl tekshiruvga yuborildi"
    );
    store.users.filter((itemUser) => itemUser.role === "admin").forEach((adminUser) => {
      addNotification(
        store,
        adminUser.id,
        "moderation",
        !scan.isSafe
          ? `${item.title} xavfsizlik tekshiruvida rad etildi`
          : `${item.title} admin tekshiruviga yuborildi`
      );
    });
    writeStore(store);
    res.status(201).json(formatFile(item));
  }
);

app.post("/api/files/:id/comments", requireAuth, (req, res) => {
  const store = readStore();
  const user = getUser(store, req.auth.sub);
  const file = store.files.find((item) => item.id === req.params.id);
  if (!user || !file) {
    return res.status(404).json({ message: "File or user not found" });
  }
  const commentGuard = ensureUserCan(user, "comment");
  if (commentGuard) {
    return res.status(403).json({ message: commentGuard, blocked: isUserBlocked(user) });
  }
  const message = String(req.body.message || "").trim();
  if (!message) {
    return res.status(400).json({ message: "message required" });
  }
  const comment = {
    id: nanoid(),
    authorId: user.id,
    authorName: user.nickname,
    authorRole: user.role,
    message,
    createdAt: new Date().toISOString()
  };
  file.comments.unshift(comment);
  if (file.authorId !== user.id) {
    addNotification(store, file.authorId, "comment", `${user.nickname} izoh qoldirdi`);
  }
  writeStore(store);
  res.status(201).json(comment);
});

app.post("/api/files/:id/rating", requireAuth, (req, res) => {
  const store = readStore();
  const file = store.files.find((item) => item.id === req.params.id);
  if (!file) {
    return res.status(404).json({ message: "File not found" });
  }
  const value = Number(req.body.value);
  if (!Number.isFinite(value) || value < 1 || value > 5) {
    return res.status(400).json({ message: "value must be between 1 and 5" });
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
  const auth = getOptionalAuth(req);
  if (auth?.sub) {
    const user = getUser(store, auth.sub);
    if (user) {
      user.downloadHistory ??= [];
      if (!user.downloadHistory.includes(file.id)) {
        user.downloadHistory.push(file.id);
      }
    }
  }
  file.downloads += 1;
  writeStore(store);
  res.json({ downloads: file.downloads });
});

app.put("/api/files/:id/manage", requireAuth, (req, res) => {
  const store = readStore();
  const user = getUser(store, req.auth.sub);
  const file = store.files.find((item) => item.id === req.params.id);
  if (!user || !file) {
    return res.status(404).json({ message: "File or user not found" });
  }
  if (!canManageFile(user, file)) {
    return res.status(403).json({ message: "You can manage only your own files" });
  }

  const nextTitle = String(req.body.title ?? file.title).trim();
  const nextDescription = String(req.body.description ?? file.description).trim();
  const nextCategory = String(req.body.category ?? file.category).trim();
  const nextVersion = String(req.body.version ?? file.version).trim();
  const nextDownloadUrl = String(req.body.downloadUrl ?? file.downloadUrl ?? "").trim();

  if (!nextTitle || !nextDescription || !nextCategory) {
    return res.status(400).json({ message: "title, description and category are required" });
  }

  try {
    if (req.body.status !== undefined) {
      applyEditableStatus(file, user, req.body.status);
    }
  } catch (error) {
    return res.status(400).json({ message: error.message || "Status update failed" });
  }

  file.title = nextTitle;
  file.description = nextDescription;
  file.category = nextCategory;
  file.version = nextVersion || file.version || "1.0.0";
  file.downloadUrl = nextDownloadUrl;

  if (file.status === "inactive" && user.role === "admin" && req.body.status === "approved") {
    addNotification(store, file.authorId, "moderation", `Fayl yana aktiv qilindi: ${file.title}`);
  }
  if (user.role === "admin" && file.authorId !== user.id && req.body.status === "inactive") {
    addNotification(store, file.authorId, "moderation", `Fayl vaqtincha yashirildi: ${file.title}`);
  }

  writeStore(store);
  res.json(formatFile(file));
});

app.post(
  "/api/files/:id/manage/assets",
  requireAuth,
  upload.fields([
    { name: "file", maxCount: 1 },
    { name: "icon", maxCount: 1 },
    { name: "screenshots", maxCount: 3 }
  ]),
  (req, res) => {
    const store = readStore();
    const user = getUser(store, req.auth.sub);
    const file = store.files.find((item) => item.id === req.params.id);
    const uploaded = Object.values(req.files || {}).flat();
    if (!user || !file) {
      cleanupUploaded([uploaded]);
      return res.status(404).json({ message: "File or user not found" });
    }
    if (!canManageFile(user, file)) {
      cleanupUploaded([uploaded]);
      return res.status(403).json({ message: "You can manage only your own files" });
    }

    const replacedFile = req.files?.file?.[0];
    const newIcon = req.files?.icon?.[0];
    const newScreenshots = req.files?.screenshots || [];
    const removeCover = String(req.body.removeCover || "").trim() === "true";
    const clearScreenshots = String(req.body.clearScreenshots || "").trim() === "true";
    const nextVersion = String(req.body.version || "").trim();
    const nextPackageName = String(req.body.packageName || "").trim();

    if (!replacedFile && !newIcon && newScreenshots.length === 0 && !removeCover && !clearScreenshots) {
      return res.status(400).json({ message: "No asset changes provided" });
    }
    if (newScreenshots.some((shot) => shot.size > MAX_SCREENSHOT_BYTES)) {
      cleanupUploaded([uploaded]);
      return res.status(400).json({ message: "Each screenshot must be 1 MB or smaller" });
    }

    const previousFileUrl = file.fileUrl;
    const previousCoverUrl = file.coverUrl;
    const previousScreenshots = [...(file.screenshotUrls || [])];

    if (replacedFile) {
      const scan = runSecurityScan(replacedFile);
      file.fileUrl = `/uploads/${replacedFile.filename}`;
      file.fileType = inferFileType(replacedFile.originalname);
      file.size = `${Math.ceil(replacedFile.size / 1024 / 1024)} MB`;
      file.version = nextVersion || file.version || "1.0.0";
      if (nextPackageName) {
        file.packageName = nextPackageName;
      }
      file.scanStatus = scan.scanStatus;
      file.scanEngine = scan.scanEngine;
      file.isSafe = scan.isSafe;
      if (user.role !== "admin" && (file.status === "approved" || file.status === "inactive")) {
        file.status = scan.isSafe ? "pending" : "rejected";
      } else if (!scan.isSafe) {
        file.status = "rejected";
      }
    }

    if (newIcon) {
      file.coverUrl = `/uploads/${newIcon.filename}`;
    }

    if (newScreenshots.length > 0) {
      file.screenshotUrls = newScreenshots.map((shot) => `/uploads/${shot.filename}`);
      if (!newIcon && (file.fileType !== "apk" || removeCover || !file.coverUrl)) {
        file.coverUrl = file.screenshotUrls[0] || file.coverUrl;
      }
    } else if (clearScreenshots) {
      file.screenshotUrls = [];
      if (previousScreenshots.includes(file.coverUrl)) {
        file.coverUrl = "";
      }
    }

    if (removeCover && !newIcon) {
      file.coverUrl = file.screenshotUrls[0] || "";
    }
    if (!file.coverUrl && file.screenshotUrls.length > 0) {
      file.coverUrl = file.screenshotUrls[0];
    }

    unlinkUploadAsset(previousFileUrl, [file.fileUrl, file.coverUrl, ...file.screenshotUrls]);
    unlinkUploadAsset(previousCoverUrl, [file.fileUrl, file.coverUrl, ...file.screenshotUrls]);
    previousScreenshots.forEach((shot) => unlinkUploadAsset(shot, [file.fileUrl, file.coverUrl, ...file.screenshotUrls]));

    if (user.role !== "admin" && replacedFile) {
      store.users.filter((itemUser) => itemUser.role === "admin").forEach((adminUser) => {
        addNotification(store, adminUser.id, "moderation", `${file.title} yangi versiya bilan yangilandi`);
      });
    }

    writeStore(store);
    res.json(formatFile(file));
  }
);

app.delete("/api/files/:id/manage", requireAuth, (req, res) => {
  const store = readStore();
  const user = getUser(store, req.auth.sub);
  const file = store.files.find((item) => item.id === req.params.id);
  if (!user || !file) {
    return res.status(404).json({ message: "File or user not found" });
  }
  if (!canManageFile(user, file)) {
    return res.status(403).json({ message: "You can manage only your own files" });
  }
  removeFileRecord(store, file);
  writeStore(store);
  res.json({ ok: true, deletedId: file.id });
});

app.get("/api/profile", requireAuth, (req, res) => {
  const store = readStore();
  const user = getUser(store, req.auth.sub);
  if (!user) {
    return res.status(404).json({ message: "User not found" });
  }
  const uploads = store.files.filter((file) => file.authorId === user.id).map(formatFile);
  const notifications = (store.notifications || []).filter((item) => item.userId === user.id).slice(0, 20);
  const userStats = buildUserStats(store, user.id);
  res.json({
    ...publicUser(user),
    uploads,
    notifications,
    stats: {
      uploadedCount: userStats.uploadedCount,
      commentedCount: userStats.commentedCount,
      downloadedCount: userStats.downloadedCount
    }
  });
});

app.post("/api/notifications/clear", requireAuth, (req, res) => {
  const store = readStore();
  const ids = Array.isArray(req.body.ids) ? req.body.ids.map((id) => String(id)) : [];
  const chatUserId = String(req.body.chatUserId || "").trim();
  const clearAll = Boolean(req.body.clearAll);
  const before = (store.notifications || []).length;
  store.notifications = (store.notifications || []).filter((notification) => {
    if (notification.userId !== req.auth.sub) {
      return true;
    }
    if (clearAll) {
      return false;
    }
    if (chatUserId && notification.kind === "chat" && notification.chatUserId === chatUserId) {
      return false;
    }
    if (ids.length > 0 && ids.includes(notification.id)) {
      return false;
    }
    return true;
  });
  if (store.notifications.length !== before) {
    writeStore(store);
  }
  res.json({ ok: true, removed: before - store.notifications.length });
});

app.get("/api/users", requireAuth, (req, res) => {
  const store = readStore();
  const users = store.users
    .filter((user) => user.id !== req.auth.sub)
    .map(publicUser)
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  res.json(users);
});

app.put("/api/profile", requireAuth, (req, res) => {
  const store = readStore();
  const user = getUser(store, req.auth.sub);
  if (!user) {
    return res.status(404).json({ message: "User not found" });
  }
  const nextNickname = String(req.body.nickname || "").trim();
  const nextUsername = String(req.body.username || user.username || "").trim();
  if (!nextNickname || !nextUsername) {
    return res.status(400).json({ message: "nickname and username are required" });
  }
  if (!ensureUniqueUsername(store, nextUsername, user.id)) {
    return res.status(409).json({ message: "Username already exists" });
  }
  const previousNickname = user.nickname;
  user.nickname = nextNickname;
  user.username = nextUsername;
  user.avatarUrl = req.body.avatarUrl || user.avatarUrl;
  store.files.forEach((file) => {
    if (file.authorId === user.id) {
      file.authorName = user.nickname;
    }
    (file.comments || []).forEach((comment) => {
      if (comment.authorId === user.id) {
        comment.authorName = user.nickname;
      }
    });
  });
  (store.messages || []).forEach((message) => {
    if (message.fromUserId === user.id) {
      message.fromNickname = user.nickname;
    }
    if (message.toUserId === user.id) {
      message.toNickname = user.nickname;
    }
  });
  if (previousNickname !== user.nickname) {
    addNotification(store, user.id, "profile", "Profil ma'lumotlari yangilandi");
  }
  writeStore(store);
  res.json(publicUser(user));
});

app.post("/api/profile/password", requireAuth, async (req, res) => {
  const store = readStore();
  const user = getUser(store, req.auth.sub);
  if (!user) {
    return res.status(404).json({ message: "User not found" });
  }
  const currentPassword = String(req.body.currentPassword || "");
  const newPassword = String(req.body.newPassword || "");
  if (!currentPassword || !newPassword) {
    return res.status(400).json({ message: "currentPassword and newPassword are required" });
  }
  if (newPassword.length < 6) {
    return res.status(400).json({ message: "New password must be at least 6 characters" });
  }
  const valid = await bcrypt.compare(currentPassword, user.passwordHash);
  if (!valid) {
    return res.status(401).json({ message: "Current password is incorrect" });
  }
  user.passwordHash = await bcrypt.hash(newPassword, 10);
  addNotification(store, user.id, "security", "Parol yangilandi");
  writeStore(store);
  res.json({ ok: true });
});

app.post("/api/profile/avatar", requireAuth, avatarUpload.single("avatar"), (req, res) => {
  const store = readStore();
  const user = getUser(store, req.auth.sub);
  if (!user) {
    return res.status(404).json({ message: "User not found" });
  }
  if (!req.file) {
    return res.status(400).json({ message: "Avatar file is required" });
  }
  user.avatarUrl = `/uploads/${req.file.filename}`;
  writeStore(store);
  res.json(publicUser(user));
});

app.delete("/api/profile", requireAuth, async (req, res) => {
  const store = readStore();
  const user = getUser(store, req.auth.sub);
  if (!user) {
    return res.status(404).json({ message: "User not found" });
  }
  if (isOwnerAdmin(user)) {
    return res.status(400).json({ message: "Owner admin cannot be deleted" });
  }
  const currentPassword = String(req.body?.currentPassword || "");
  if (!currentPassword) {
    return res.status(400).json({ message: "currentPassword is required" });
  }
  const valid = await bcrypt.compare(currentPassword, user.passwordHash);
  if (!valid) {
    return res.status(401).json({ message: "Current password is incorrect" });
  }
  const summary = deleteUserAccount(store, user.id);
  writeStore(store);
  res.json({ ok: true, ...summary });
});

app.get("/api/chat/threads", requireAuth, (req, res) => {
  const store = readStore();
  const messages = (store.messages || []).filter(
    (message) =>
      (message.fromUserId === req.auth.sub || message.toUserId === req.auth.sub) &&
      !(Array.isArray(message.deletedFor) && message.deletedFor.includes(req.auth.sub))
  );
  res.json(messages.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)));
});

app.post("/api/chat/send", requireAuth, (req, res) => {
  const store = readStore();
  const fromUser = getUser(store, req.auth.sub);
  const toUser = store.users.find((user) => user.id === req.body.toUserId);
  if (!fromUser || !toUser) {
    return res.status(404).json({ message: "User not found" });
  }
  const chatGuard = ensureUserCan(fromUser, "chat");
  if (chatGuard) {
    return res.status(403).json({ message: chatGuard, blocked: isUserBlocked(fromUser) });
  }
  const message = String(req.body.message || "").trim();
  if (!message) {
    return res.status(400).json({ message: "message required" });
  }
  store.messages ??= [];
  const item = {
    id: nanoid(),
    fromUserId: fromUser.id,
    fromNickname: fromUser.nickname,
    toUserId: toUser.id,
    toNickname: toUser.nickname,
    message,
    createdAt: new Date().toISOString(),
    status: "sent",
    deliveredAt: null,
    readAt: null,
    deletedFor: []
  };
  store.messages.unshift(item);
  addNotification(store, toUser.id, "chat", message, {
    title: fromUser.nickname,
    chatUserId: fromUser.id
  });
  writeStore(store);
  res.status(201).json(item);
});

app.post("/api/chat/read/:userId", requireAuth, (req, res) => {
  const store = readStore();
  let updated = 0;
  (store.messages || []).forEach((message) => {
    if (message.fromUserId === req.params.userId && message.toUserId === req.auth.sub && !message.readAt) {
      message.deliveredAt = message.deliveredAt || new Date().toISOString();
      message.readAt = new Date().toISOString();
      message.status = "read";
      updated += 1;
    }
  });
  const beforeNotifications = (store.notifications || []).length;
  store.notifications = (store.notifications || []).filter(
    (notification) => !(
      notification.userId === req.auth.sub &&
      notification.kind === "chat" &&
      notification.chatUserId === req.params.userId
    )
  );
  const notificationsRemoved = beforeNotifications - store.notifications.length;
  if (updated > 0 || notificationsRemoved > 0) {
    writeStore(store);
  }
  res.json({ ok: true, updated, notificationsRemoved });
});

app.post("/api/chat/clear", requireAuth, (req, res) => {
  const store = readStore();
  const otherUserId = String(req.body.userId || "").trim();
  if (!otherUserId) {
    return res.status(400).json({ message: "userId required" });
  }
  let updated = 0;
  (store.messages || []).forEach((message) => {
    const isConversationMessage =
      (message.fromUserId === req.auth.sub && message.toUserId === otherUserId) ||
      (message.fromUserId === otherUserId && message.toUserId === req.auth.sub);
    if (!isConversationMessage) return;
    message.deletedFor = Array.isArray(message.deletedFor) ? message.deletedFor : [];
    if (!message.deletedFor.includes(req.auth.sub)) {
      message.deletedFor.push(req.auth.sub);
      updated += 1;
    }
  });
  const beforeNotifications = (store.notifications || []).length;
  store.notifications = (store.notifications || []).filter(
    (notification) => !(
      notification.userId === req.auth.sub &&
      notification.kind === "chat" &&
      notification.chatUserId === otherUserId
    )
  );
  const notificationsRemoved = beforeNotifications - store.notifications.length;
  if (updated > 0 || notificationsRemoved > 0) {
    writeStore(store);
  }
  res.json({ ok: true, updated, notificationsRemoved });
});

app.get("/api/admin/overview", requireAuth, requireAdmin, (_req, res) => {
  const store = readStore();
  res.json({
    users: store.users.map((user) => ({
      ...publicUser(user),
      ...buildUserStats(store, user.id),
      passwordState: "encrypted"
    })),
    files: store.files.map(formatFile),
    comments: store.files.flatMap((file) => file.comments.map((comment) => ({ ...comment, fileId: file.id, fileTitle: file.title }))),
    notifications: store.notifications || [],
    messages: store.messages || [],
    stats: {
      totalUsers: store.users.length,
      blockedUsers: store.users.filter((user) => isUserBlocked(user)).length,
      pendingFiles: store.files.filter((file) => file.status === "pending").length,
      pendingDevelopers: store.users.filter((user) => user.role === "developer" && !user.developerApproved).length
    }
  });
});

app.patch("/api/admin/files/:id/status", requireAuth, requireAdmin, (req, res) => {
  const store = readStore();
  const file = store.files.find((item) => item.id === req.params.id);
  if (!file) {
    return res.status(404).json({ message: "File not found" });
  }
  file.status = ["approved", "rejected", "pending", "inactive"].includes(req.body.status) ? req.body.status : file.status;
  addNotification(store, file.authorId, "moderation", `Fayl holati: ${file.status}`);
  writeStore(store);
  res.json(formatFile(file));
});

app.delete("/api/admin/files/:id", requireAuth, requireAdmin, (req, res) => {
  const store = readStore();
  const file = store.files.find((item) => item.id === req.params.id);
  if (!file) {
    return res.status(404).json({ message: "File not found" });
  }
  removeFileRecord(store, file);
  if (file?.authorId) {
    addNotification(store, file.authorId, "moderation", `Admin faylni o'chirdi: ${file.title}`);
  }
  writeStore(store);
  res.json({ ok: true, deletedId: file.id });
});

app.delete("/api/admin/comments/:fileId/:commentId", requireAuth, requireAdmin, (req, res) => {
  const store = readStore();
  const file = store.files.find((item) => item.id === req.params.fileId);
  if (!file) {
    return res.status(404).json({ message: "File not found" });
  }
  file.comments = file.comments.filter((comment) => comment.id !== req.params.commentId);
  writeStore(store);
  res.json({ ok: true });
});

app.patch("/api/admin/users/:id/moderation", requireAuth, requireAdmin, (req, res) => {
  const store = readStore();
  const user = getUser(store, req.params.id);
  if (!user) {
    return res.status(404).json({ message: "User not found" });
  }
  if (user.role === "admin" && user.username === ADMIN_USERNAME) {
    return res.status(400).json({ message: "Owner admin cannot be restricted" });
  }
  if (typeof req.body.developerApproved === "boolean" && user.role === "developer") {
    user.developerApproved = req.body.developerApproved;
    addNotification(store, user.id, "developer", user.developerApproved ? "Developer account tasdiqlandi" : "Developer account bekor qilindi");
  }
  if (typeof req.body.canChat === "boolean") {
    user.canChat = req.body.canChat;
  }
  if (typeof req.body.canComment === "boolean") {
    user.canComment = req.body.canComment;
  }
  if (typeof req.body.canUpload === "boolean") {
    user.canUpload = req.body.canUpload;
  }
  if (typeof req.body.blocked === "boolean") {
    if (req.body.blocked) {
      const until = String(req.body.blockedUntil || "").trim();
      user.blockedUntil = until || new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString();
      user.blockReason = String(req.body.blockReason || "Admin tomonidan bloklangan").trim();
      addNotification(store, user.id, "block", `Account bloklandi: ${user.blockReason}`);
    } else {
      user.blockedUntil = "";
      user.blockReason = "";
      addNotification(store, user.id, "block", "Account blokdan chiqarildi");
    }
  }
  writeStore(store);
  res.json({
    ...publicUser(user),
    ...buildUserStats(store, user.id),
    passwordState: "encrypted"
  });
});

app.delete("/api/admin/users/:id", requireAuth, requireAdmin, (req, res) => {
  const store = readStore();
  const user = getUser(store, req.params.id);
  if (!user) {
    return res.status(404).json({ message: "User not found" });
  }
  if (user.id === req.auth.sub) {
    return res.status(400).json({ message: "Use profile delete to remove your own account" });
  }
  if (isOwnerAdmin(user)) {
    return res.status(400).json({ message: "Owner admin cannot be deleted" });
  }
  const summary = deleteUserAccount(store, user.id);
  writeStore(store);
  res.json({ ok: true, ...summary });
});

app.post("/api/admin/chat/clear", requireAuth, requireAdmin, (req, res) => {
  const store = readStore();
  const userId = String(req.body.userId || "").trim();
  const before = (store.messages || []).length;
  if (userId) {
    store.messages = (store.messages || []).filter(
      (message) => message.fromUserId !== userId && message.toUserId !== userId
    );
  } else {
    store.messages = [];
  }
  writeStore(store);
  res.json({ ok: true, removed: before - store.messages.length });
});

const clientDist = path.resolve(process.cwd(), "dist", "client");
if (fs.existsSync(clientDist)) {
  app.use(express.static(clientDist));
  app.get("*", (req, res, next) => {
    if (req.path.startsWith("/api")) return next();
    res.sendFile(path.join(clientDist, "index.html"));
  });
}

app.use((err, _req, res, next) => {
  if (!err) {
    return next();
  }
  if (err instanceof multer.MulterError && err.code === "LIMIT_FILE_SIZE") {
    return res.status(400).json({
      message: err.field === "avatar" ? "Avatar file is too large" : "File too large. Maximum is 200 MB"
    });
  }
  if (err.message) {
    return res.status(400).json({ message: err.message });
  }
  return res.status(500).json({ message: "Upload failed" });
});

app.listen(PORT, () => {
  const store = readStore();
  ensureAdminUser(store);
  console.log(`SAMP STORE server running on http://localhost:${PORT}`);
});
