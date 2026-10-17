const { ApiError } = require('../utils/errors');
function errorHandler(error, req, res, next) { // eslint-disable-line no-unused-vars
  if (error instanceof SyntaxError && 'body' in error) error = new ApiError(400, 'VALIDATION_ERROR', 'Malformed JSON.', { fields: [] });
  const status = error.status || 500;
  if (status >= 500) console.error(JSON.stringify({ level: 'error', message: error.message, path: req.path }));
  res.status(status).json({ error: { code: error.code || 'INTERNAL_ERROR', message: error.message || 'Internal error.', details: error.details || {} } });
}
function requestLog(req, res, next) { const started = Date.now(); res.on('finish', () => console.log(JSON.stringify({ method: req.method, path: req.path, status: res.statusCode, duration_ms: Date.now() - started }))); next(); }
module.exports = { errorHandler, requestLog };
