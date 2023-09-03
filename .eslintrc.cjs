module.exports = {
    extends: ["eslint:recommended", "plugin:@typescript-eslint/recommended"],
    parser: "@typescript-eslint/parser",
    plugins: ["@typescript-eslint", 'eslint-plugin-tsdoc'],
    root: true,
    env: {
        browser: true,
        node: true,
        jest: true,
    },
    rules: {
        'tsdoc/syntax': 'error'
    }
};
