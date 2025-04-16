// convert-urls.mjs
import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const docsDirectory = '../docs/markdown/node';

function convertLinksInFile(filePath) {
    const content = fs.readFileSync(filePath, 'utf8');
    console.log('Processing file:', filePath);
    
    const regex = /\[(https:\/\/[^|]+)\|([^\]]+)\]\((https:\/\/[^|]+)\|([^)]+)\)/g;
    let updatedContent = content.replace(regex, '[$2]($1)');

    // Clean up stray backslashes before closing parens
    updatedContent = updatedContent.replace(/\\(?=\))/g, '');

    fs.writeFileSync(filePath, updatedContent);
}

function processDirectory(directory) {
    const files = fs.readdirSync(directory);
    files.forEach(file => {
        const fullPath = path.join(directory, file);
        const stat = fs.statSync(fullPath);
        if (stat.isDirectory()) {
            processDirectory(fullPath);
        } else if (path.extname(file) === '.md') {
            convertLinksInFile(fullPath);
        }
    });
}

processDirectory(docsDirectory);
