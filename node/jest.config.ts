import type { Config } from "jest";

const config: Config = {
    preset: "ts-jest",
    transform: {
        "^.+\\.(t|j)s$": "ts-jest",
    },
    testEnvironment: "node",
    testRegex: "/tests/.*\\.(test|spec)?\\.(ts|tsx)$",
    moduleFileExtensions: ["ts", "json", "node", "js"],
    modulePathIgnorePatterns: ["rust-client/"],
    reporters: [
        "default",
        [
            "./node_modules/jest-html-reporter",
            {
                includeFailureMsg: true,
                includeSuiteFailure: true,
                executionTimeWarningThreshold: 60,
                sort: "status",
            },
        ],
    ],
    setupFilesAfterEnv: ["./tests/setup.ts"],
    coverageProvider: "v8",
    collectCoverage: true,
    coverageReporters: ["text", "html"],
};

export default config;
