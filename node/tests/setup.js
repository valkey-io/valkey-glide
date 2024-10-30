import { beforeAll } from "@jest/globals";
import { Logger } from "..";

beforeAll(() => {
    Logger.init("error");
    Logger.setLoggerConfig("error");
});
