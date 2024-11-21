import { it } from "@jest/globals";

const fs = require('fs/promises');
var exportedSymbols = require('../index');
let i = 0;
let filesWithNodeCode: string[] = [];
let getActualSymbolsList: string[] = [];

describe("Exported Symbols test", () => {
    var excludedSymbolList: string[] = [];
    beforeAll(() => {
        /**
         * Add Excluded symbols
         * Example:
         * excludedSymbols.push('convertGlideRecord');
         */
    });
    it("check excluded symbols are not exported", async () => {
        // Check exported symbols for valkey glide package
        let exportedSymbolsList = Object.keys(exportedSymbols);  // exportedList
        const filteredExportedList = exportedSymbolsList.filter(symbol => excludedSymbolList.includes(symbol));
        console.log("Following symbols are exported but are in the exlcuded list, please remove: " + filteredExportedList);
        expect(filteredExportedList.length).toBe(0);

        const testFolder = './';
        await getFiles(testFolder);
        console.log('Total files found =' + i);
        console.log(filesWithNodeCode);

        let actualSymbolList: string[] = [];  //Actual list

        //1. Test if actualSymbolList - exportedSymbolsList = excludedSymbolList
        //2. If actualSymbolList - exportedSymbolsList != excludedSymbolList, 
        //   throw an error that either some symbol not exported or there is some error in the error list.
    });
});

const skipFolders = ['build-ts', 'commonjs-test', 'glide-logs', 'hybrid-node-tests', 'node_modules', 'npm', '.cargo', 'target', 'tests'];
async function getFiles(folderName: string) {
    const files = await fs.readdir(folderName, { withFileTypes: true });
    for (let file of files) {
        if (file.isDirectory()) {
            if (skipFolders.includes(file.name)) {
                continue;
            }
            await getFiles(folderName + file.name + '/');
        } else {
            if (file.name.endsWith('.js') ||
                file.name.endsWith('.d.ts') ||
                file.name.endsWith('.json') ||
                file.name.endsWith('.rs') ||
                file.name.endsWith('.html') ||
                file.name.endsWith('.node') ||
                file.name.endsWith('.lock') ||
                file.name.endsWith('.toml') ||
                file.name.endsWith('.yml') ||
                file.name.endsWith('.rdb') ||
                file.name.endsWith('.md') ||
                file.name.localeCompare('.gitignore') == 0 ||
                file.name.localeCompare('.prettierignore') == 0 ||
                file.name.localeCompare('THIRD_PARTY_LICENSES_NODE') == 0 ||
                file.name.localeCompare('index.ts') == 0) {
                continue;
            }
            i++;
            filesWithNodeCode.push(folderName + file.name);
        }
    }
}
