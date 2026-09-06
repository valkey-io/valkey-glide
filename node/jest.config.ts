import * as fs from "fs";
import type { Config } from "jest";

const useElastiCache = process.env.USE_ELASTICACHE === "true";
const useRemote = fs.existsSync("C:\\glide-remote.json");
// Use Jest globalSetup/globalTeardown when:
// 1. ElastiCache is enabled and no endpoints are pre-provided, OR
// 2. Running on Windows EC2 with a remote Linux EC2 (glide-remote.json present)
const needsJestManagedClusters =
    (useElastiCache &&
        !process.env.STANDALONE_ENDPOINT &&
        !process.env.CLUSTER_ENDPOINT) ||
    useRemote;

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

    // Global setup/teardown for ElastiCache cluster lifecycle
    // Only active when USE_ELASTICACHE=true and no endpoints are pre-provided
    ...(needsJestManagedClusters && {
        globalSetup: "<rootDir>/tests/jest.globalSetup.ts",
        globalTeardown: "<rootDir>/tests/jest.globalTeardown.ts",
    }),

    // Coverage settings
    coverageProvider: "v8",
    collectCoverage: true,
    coverageReporters: ["text", "html"],
};

export default config;
