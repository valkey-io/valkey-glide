import { beforeAll } from "@jest/globals";
import { Logger } from "..";

beforeAll(() => {
    Logger.init("off");
    Logger.setLoggerConfig("off");
});
