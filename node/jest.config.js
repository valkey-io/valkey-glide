module.exports = {
    preset: "ts-jest",
    transform: { "^.+\\.ts?$": "ts-jest" },
    testEnvironment: "node",
    testRegex: "/tests/.*\\.(test|spec)?\\.(ts|tsx)$",
    moduleFileExtensions: ["ts", "tsx", "js", "jsx", "json", "node"],
    testTimeout: 20000,
};
