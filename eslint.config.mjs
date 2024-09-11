// @ts-check
import eslint from "@eslint/js";
import prettierConfig from "eslint-config-prettier";
import tseslint from "typescript-eslint";

export default tseslint.config(
    eslint.configs.recommended,
    ...tseslint.configs.recommended,
    ...tseslint.configs.stylistic,
    { files: ["**/*.js"], ...tseslint.configs.disableTypeChecked },
    {
        ignores: [
            "*/ProtobufMessage.*",
            "**/*.d.ts",
            "node_modules/**",
            "build-ts/**",
            "jest.config.js",
            "docs/**"
        ],
    },
    {
        rules: {
            "import/no-unresolved": "off",
            "padding-line-between-statements": [
                "error",
                {
                    blankLine: "always",
                    prev: "*",
                    next: "class",
                },
                {
                    blankLine: "always",
                    prev: "class",
                    next: "*",
                },
                {
                    blankLine: "always",
                    prev: "*",
                    next: "function",
                },
                {
                    blankLine: "always",
                    prev: "function",
                    next: "*",
                },
                {
                    blankLine: "always",
                    prev: "*",
                    next: "multiline-block-like",
                },
                {
                    blankLine: "always",
                    prev: "multiline-block-like",
                    next: "*",
                },
            ],
        },
    },
    prettierConfig,
);
