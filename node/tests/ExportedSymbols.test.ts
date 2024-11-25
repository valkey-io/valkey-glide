import { it } from "@jest/globals";
import * as ts from "typescript";

const fs = require('fs/promises');
const f = require('fs');

var exportedSymbols = require('../npm/glide/index');
let i = 0;
let filesWithNodeCode: string[] = [];
let getActualSymbolsList: any[] = [];

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
        //        const filteredExportedList = exportedSymbolsList.filter(symbol => excludedSymbolList.includes(symbol));
        //        console.log("Following symbols are exported but are in the exlcuded list, please remove: " + filteredExportedList);
        //        expect(filteredExportedList.length).toBe(0);

        const testFolder = './';
        await getFiles(testFolder);
        console.log('Total files found =' + i); // 10
        console.log(filesWithNodeCode); //[]
        for (let file of filesWithNodeCode) {
            const sourceCode = await f.readFileSync(file, 'utf8');
            const sourceFile = await ts.createSourceFile(file, sourceCode, ts.ScriptTarget.Latest, true);
            visit(sourceFile, 0);
        }
        console.log("actual exports in the source code =");
        //console.log(getActualSymbolsList);
        console.log("Total exported symbols in index=" + exportedSymbolsList.length);
        console.log("Total symbols in the source code=" + getActualSymbolsList.length);
        //        console.log(exportedSymbolsList);
        let l = getActualSymbolsList.filter(actualSymbol => !exportedSymbolsList.includes(actualSymbol));
        console.log("Total missed symbols=" + l.length);
        console.log(l);
        //1. Test if actualSymbolList - exportedSymbolsList = excludedSymbolList
        //2. If actualSymbolList - exportedSymbolsList != excludedSymbolList, 
        //   throw an error that either some symbol not exported or there is some error in the excluded symbol list.
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

function visit(node: ts.Node, depth = 0) {
    if (depth == 2) {
        let name: string | undefined = "";
        if (ts.isFunctionDeclaration(node)) {
            name = node.name?.text;
        } else if (ts.isVariableStatement(node)) {
            name = "";
        } else if (ts.isInterfaceDeclaration(node)) {
            name = node.name?.text;
        } else if (ts.isClassDeclaration(node)) {
            name = node.name?.text;
        } else if (ts.isTypeAliasDeclaration(node)) {
            name = node.name?.text;
        } else if (ts.isEnumDeclaration(node)) {
            name = node.name?.text;
        } else if (ts.isModuleDeclaration(node)) {
            name = node.name?.text;
        } else if (ts.SyntaxKind[node.kind] == "FirstStatement") {
            name = node.name?.text;
        }
        let f = 0;
        for (let c of node.getChildren()) {
            if (c.getText().trim() == "export") {
                f = 1;
            }
        }
        if (f == 1) {
            if (name == '') {
                console.log('depth=' + depth + ", ts.SyntaxKind===" + ts.SyntaxKind[node.kind] + ", name=" + name);
                console.log(node.getText());
            }
            getActualSymbolsList.push(name);

        } else {
            //            console.log('depth=' + depth + ", ts.SyntaxKind===" + ts.SyntaxKind[node.kind] + ", name=" + name + " ---not exported");
        }
    }
    if (depth <= 1) {
        for (let c of node.getChildren()) {
            visit(c, depth + 1);
        }
    }
}
