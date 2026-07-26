import { defineConfig } from 'vitest/config';
import { codecovVitePlugin } from '@codecov/vite-plugin';

// This config is used ONLY for bundle analysis (Codecov) during `npm run build`.
// Tests run via `ng test` (Angular CLI) — standalone Vitest is not yet compatible
// with Angular 22's compiler.
export default defineConfig({
  plugins: [
    codecovVitePlugin({
      enableBundleAnalysis: process.env['CODECOV_TOKEN'] !== undefined,
      bundleName: 'speisegeist-frontend',
      uploadToken: process.env['CODECOV_TOKEN'],
    }),
  ],
});
