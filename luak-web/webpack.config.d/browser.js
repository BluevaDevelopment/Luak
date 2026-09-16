// luak-core's JS host reaches for node:fs and node:os only after checking that
// a Node-style `require` exists. A page has neither module, so the bundle gets
// empty ones instead of failing to build over code a browser never runs.
const webpack = require('webpack');

config.plugins = config.plugins || [];
config.plugins.push(new webpack.NormalModuleReplacementPlugin(/^node:/, (resource) => {
  resource.request = resource.request.replace(/^node:/, '');
}));
config.resolve = config.resolve || {};
config.resolve.fallback = Object.assign({}, config.resolve.fallback, { fs: false, os: false });
