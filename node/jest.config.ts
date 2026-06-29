import type { Config } from "jest";

const config: Config = {
    preset: "ts-jest",
    transform: {
        "^.+\\.(ts|tsx)$": [
            "ts-jest",
            {
                tsconfig: "tests/tsconfig.json",
            },
        ],
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

    // Retry failing tests up to 2 times in CI to absorb transient failures
    testRetries: process.env.CI ? 2 : 0,

    // Shared cluster setup/teardown - start servers once for all compatible test files
    globalSetup: "<rootDir>/tests/globalSetup.ts",
    globalTeardown: "<rootDir>/tests/globalTeardown.ts",

    // Coverage settings
    coverageProvider: "v8",
    collectCoverage: true,
    coverageReporters: ["text", "html"],
};

export default config;
