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

// A plain script that runs as soon as it loads. The Kotlin plugin's UMD
// wrapper hands the bundle to an AMD loader when a page has one (Monaco's
// does), and then nothing ever runs it and LuakWeb never appears.
delete config.output.library;
delete config.output.libraryTarget;
