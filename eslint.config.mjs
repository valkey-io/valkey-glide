import { fixupConfigRules, fixupPluginRules } from "@eslint/compat";
import typescriptEslint from "@typescript-eslint/eslint-plugin";
import tsdoc from "eslint-plugin-tsdoc";
import _import from "eslint-plugin-import";
import globals from "globals";
import tsParser from "@typescript-eslint/parser";
import path from "node:path";
import { fileURLToPath } from "node:url";
import js from "@eslint/js";
import { FlatCompat } from "@eslint/eslintrc";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const compat = new FlatCompat({
    baseDirectory: __dirname,
    recommendedConfig: js.configs.recommended,
    allConfig: js.configs.all
});

export default [...fixupConfigRules(compat.extends(
    "eslint:recommended",
    "plugin:@typescript-eslint/recommended",
    "plugin:import/errors",
    "plugin:import/warnings",
)), {
    plugins: {
        "@typescript-eslint": fixupPluginRules(typescriptEslint),
        tsdoc,
        import: fixupPluginRules(_import),
    },

    languageOptions: {
        globals: {
            ...globals.browser,
            ...globals.node,
            ...globals.jest,
        },

        parser: tsParser,
    },

    rules: {
        "tsdoc/syntax": "error",
        "import/no-unresolved": "off",

        "padding-line-between-statements": ["error", {
            blankLine: "always",
            prev: "*",
            next: "class",
        }, {
            blankLine: "always",
            prev: "class",
            next: "*",
        }, {
            blankLine: "always",
            prev: "*",
            next: "function",
        }, {
            blankLine: "always",
            prev: "function",
            next: "*",
        }, {
            blankLine: "always",
            prev: "*",
            next: "multiline-block-like",
        }, {
            blankLine: "always",
            prev: "multiline-block-like",
            next: "*",
        }],
    },
}];