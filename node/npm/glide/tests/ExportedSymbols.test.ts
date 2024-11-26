import { beforeAll, it } from "@jest/globals";
import * as f from 'fs/promises';
import { describe } from "node:test";
import * as ts from "typescript";
import * as glideApi from "../";

describe("Exported Symbols test", () => {

    beforeAll(() => {
        /**
         * Add Excluded symbols
         * Example:
         * excludedSymbols.push('convertGlideRecord');
         */
    });

    it("check excluded symbols are not exported", async () => {

        // Check exported symbols for valkey glide package
        const exportedSymbolsList = Object.keys(glideApi).sort();  // exportedList
        console.log(exportedSymbolsList);
        console.log(exportedSymbolsList.length);

        const testFolder = './build-ts/';
        const filesWithNodeCode = await getFiles(testFolder);
        console.log(filesWithNodeCode);

        const actualExported = [];
        
        for (const file of filesWithNodeCode) {
            const sourceCode = await f.readFile(file, 'utf8');
            const sourceFile = await ts.createSourceFile(file, sourceCode, ts.ScriptTarget.Latest, true);
            actualExported.push(...visitRoot(sourceFile));
        }

        actualExported.sort();
        console.log(actualExported);
        console.log(actualExported.length);

        expect(exportedSymbolsList).toEqual(actualExported);
    });
});


// function getFiles(folderName: fs.PathLike): string[] {
//     const files = fs.readdirSync(folderName).filter(file => file.endsWith('.ts') && !file.endsWith('.d.ts'));
//     return files;
// }

async function getFiles(folderName: string): Promise<string[]> {
    // console.log(folderName);

    const files = await f.readdir(folderName, { withFileTypes: true });

    // const skipFolders = [
    //     'build-ts', 
    //     'commonjs-test', 
    //     'glide-logs', 
    //     'hybrid-node-tests', 
    //     'node_modules', 
    //     'npm', 
    //     '.cargo', 
    //     'target', 
    //     'tests'
    // ];

    const filesWithNodeCode = [];

    for (const  file of files) {
        if (file.isDirectory()) {
            // if (skipFolders.includes(file.name)) {
            //     continue;
            // }

            filesWithNodeCode.push(...(await getFiles(folderName + file.name + '/')));
        } else {
            // console.log("not a dir: " + file.name);

            // if (file.name.endsWith('.js') ||
            //     file.name.endsWith('.d.ts') ||
            //     file.name.endsWith('.json') ||
            //     file.name.endsWith('.rs') ||
            //     file.name.endsWith('.html') ||
            //     file.name.endsWith('.node') ||
            //     file.name.endsWith('.lock') ||
            //     file.name.endsWith('.toml') ||
            //     file.name.endsWith('.yml') ||
            //     file.name.endsWith('.rdb') ||
            //     file.name.endsWith('.md') ||
            //     file.name.localeCompare('.gitignore') == 0 ||
            //     file.name.localeCompare('.prettierignore') == 0 ||
            //     file.name.localeCompare('THIRD_PARTY_LICENSES_NODE') == 0 ||
            //     file.name.localeCompare('index.ts') == 0) {
            //     continue;
            // }

            if (!file.name.endsWith('.d.ts')) {
                continue;
            }

            // i++;
            filesWithNodeCode.push(folderName + file.name);
        }
    }
    
    return filesWithNodeCode;
}

function visitRoot(root: ts.Node) {
    const children: ts.Node[] = root.getChildren();

    const resultList: string[] = [];

    for (const node of children) {
        const nodeList: string[] = node.getChildren().map(c => visit(c)).filter(c => c !== undefined);

        if (nodeList.length > 0) {
            resultList.push(...nodeList);
        }

        console.log(resultList);
    }
    
    return resultList;
}

function visit(node: ts.Node) {
    let name: string | undefined = "";
    
    switch (node.kind) {
        case ts.SyntaxKind.FirstStatement:
        case ts.SyntaxKind.ExportDeclaration:
        case ts.SyntaxKind.ExportAssignment:
        case ts.SyntaxKind.ImportDeclaration:
            return;
    }

    // list of kind we like:
    // InterfaceDeclaration
    // FunctionDeclaration

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
    }

    const debug: string[] = [];
    const children = node.getChildren();
    const isInternal = children.find(c => (ts.SyntaxKind[c.kind] == "JSDocComment"))?.getText().includes('@internal');
    const isExported = children.find(c => (ts.SyntaxKind[c.kind] == "SyntaxList"))?.getChildren().find(c => (ts.SyntaxKind[c.kind] == "ExportKeyword"));

    if (isExported && !isInternal) {
        // debug.push('depth=' + depth + ", ts.SyntaxKind===" + ts.SyntaxKind[node.kind] + ", name=" + name);
        // if (name) {
        //     debug.push(`name=${name} kind=${ts.SyntaxKind[node.kind]}`);
        // } else {
        //     debug.push(`name=unnamed kind=${ts.SyntaxKind[node.kind]}`);
        // }

        // console.log(debug);
        return name;
    }
    
    if (isExported && isInternal) {
        // marked correctly... no-op
    }
    
    if (!isExported && isInternal) {
        // no-op
    }

    if (!isExported && !isInternal) {
        // these are okay for now... 
        // debug.push(`PRIVATE??? name=unnamed kind=${ts.SyntaxKind[node.kind]} text=${node.getText()}`);
    }
    
    // if (debug.length > 0) {
    //     console.log(debug);
    // }
}
