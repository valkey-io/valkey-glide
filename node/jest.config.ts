import type { Config } from "jest";

const useElastiCache = process.env.USE_ELASTICACHE === "true";
// Use Jest globalSetup/globalTeardown only when ElastiCache is enabled
// AND endpoints have not already been provided externally (e.g. by the
// buildspec background job). When endpoints are pre-set, Jest should
// skip cluster creation and just use the existing clusters.
const needsJestManagedClusters =
    useElastiCache &&
    !process.env.STANDALONE_ENDPOINT &&
    !process.env.CLUSTER_ENDPOINT;

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
