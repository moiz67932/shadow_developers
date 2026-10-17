class ApiError extends Error { constructor(status, code, message, details = {}) { super(message); this.status = status; this.code = code; this.details = details; } }
const fail = (status, code, message, details) => { throw new ApiError(status, code, message, details); };
module.exports = { ApiError, fail };
