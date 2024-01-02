module.exports = {
    extends: [
        "eslint:recommended",
        "plugin:@typescript-eslint/recommended",
        "plugin:import/errors",
        "plugin:import/warnings",
    ],
    parser: "@typescript-eslint/parser",
    plugins: ["@typescript-eslint", "eslint-plugin-tsdoc", "import"],
    root: true,
    env: {
        browser: true,
        node: true,
        jest: true,
    },
    rules: {
        "tsdoc/syntax": "error",
        "import/no-unresolved": "off",
        "padding-line-between-statements": [
            "error",
            { blankLine: "always", prev: "*", next: "class" },
            { blankLine: "always", prev: "class", next: "*" },
            { blankLine: "always", prev: "*", next: "function" },
            { blankLine: "always", prev: "function", next: "*" },
            { blankLine: "always", prev: "*", next: "multiline-block-like" },
            { blankLine: "always", prev: "multiline-block-like", next: "*" },
        ],
    },
};
