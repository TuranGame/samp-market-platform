# SAMP MARKET Platform

Render-ready full-stack platform for the Android app.

## Stack

- `Express` backend with REST API
- `React + Vite` frontend
- JSON persistence in `DATA_DIR/store.json`
- Uploaded files stored in `DATA_DIR/uploads`

## Local run

```powershell
npm install
npm run build
npm start
```

Open:

- `http://localhost:3000`
- `http://localhost:3000/api/health`

## Render deploy

This repo includes [render.yaml](/c:/Users/Daler/Desktop/samp%20market/render.yaml).

Render setup:

1. Push this project to GitHub.
2. In Render choose `Blueprint`.
3. Select the repository.
4. Render will create the Node web service and persistent disk automatically.

Important env vars:

- `JWT_SECRET`
- `DATA_DIR=/var/data`

## Main API

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/home`
- `GET /api/files`
- `GET /api/files/:id`
- `POST /api/files/upload`
- `POST /api/files/:id/comments`
- `POST /api/files/:id/rating`
- `GET /api/profile`
- `PUT /api/profile`

## Android connection later

In the Android app profile/server URL, use:

```text
https://your-render-domain.onrender.com/api
```

Uploads are available from:

```text
https://your-render-domain.onrender.com/uploads
```
