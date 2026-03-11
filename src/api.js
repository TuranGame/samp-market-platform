const API_BASE = "/api";

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      ...(options.body instanceof FormData ? {} : { "Content-Type": "application/json" }),
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {})
    },
    ...options
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message || "Request failed");
  }

  return data;
}

export const api = {
  home: () => request("/home"),
  files: (params = {}) => {
    const query = new URLSearchParams(params);
    return request(`/files${query.toString() ? `?${query}` : ""}`);
  },
  file: (id) => request(`/files/${id}`),
  register: (payload) =>
    request("/auth/register", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  login: (payload) =>
    request("/auth/login", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  profile: (token) =>
    request("/profile", {
      token
    }),
  updateProfile: (token, payload) =>
    request("/profile", {
      method: "PUT",
      token,
      body: JSON.stringify(payload)
    }),
  uploadFile: (token, formData) =>
    request("/files/upload", {
      method: "POST",
      token,
      body: formData
    }),
  addComment: (token, fileId, message) =>
    request(`/files/${fileId}/comments`, {
      method: "POST",
      token,
      body: JSON.stringify({ message })
    }),
  addRating: (token, fileId, value) =>
    request(`/files/${fileId}/rating`, {
      method: "POST",
      token,
      body: JSON.stringify({ value })
    }),
  addDownload: (fileId) =>
    request(`/files/${fileId}/download`, {
      method: "POST"
    })
};
