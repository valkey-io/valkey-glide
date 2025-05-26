import type { Config } from "jest";

const config: Config = {
    preset: "ts-jest",
    transform: {
        "^.+\\.(ts|tsx)$": "ts-jest",
    },
    transformIgnorePatterns: [
        "node_modules/",
        "\\.(js|jsx)$",
        "<rootDir>/build-ts/",
    ],
    testEnvironment: "node",
    // Look for tests inside the /tests/ directory with .test or .spec extensions
    testRegex: "/tests/.*\\.(test|spec)?\\.(ts|tsx)$",
    moduleFileExtensions: ["ts", "js", "json", "node"],
    modulePathIgnorePatterns: ["rust-client/", "build-js/"],

    // Reporters: default + HTML report
    reporters: [
        "default",
        [
            "jest-html-reporter",
            {
                includeFailureMsg: true,
                includeSuiteFailure: true,
                executionTimeWarningThreshold: 60,
                sort: "status",
            },
        ],
    ],

    // Setup file to configure the testing environment after Jest is installed
    setupFilesAfterEnv: ["<rootDir>/tests/setup.ts"],

    // Coverage settings
    coverageProvider: "v8",
    collectCoverage: true,
    coverageReporters: ["text", "html"],
};

export default config;
