import { ERR_MESSAGE_LOAD_FAILED, getExports, getPackagePath } from "../common/index.cjs";

const packagePath = getPackagePath();
const packageModule = require(packagePath);
if (!packageModule) {
    throw new Error(ERR_MESSAGE_LOAD_FAILED);
}

const moduleExports = getExports(packageModule);
export default {
    ...moduleExports
};
