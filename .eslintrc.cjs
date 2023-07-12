module.exports = {
    extends: ["eslint:recommended", "plugin:@typescript-eslint/recommended"],
    parser: "@typescript-eslint/parser",
    plugins: ["@typescript-eslint"],
    rules: {
        "@typescript-eslint/no-explicit-any": "off",
        // DELETE AFTER ROUTE IMPL
        "@typescript-eslint/no-unused-vars": "off"
    },
    root: true,
    env: {
        browser: true,
        node: true,
        jest: true,
    },
};
